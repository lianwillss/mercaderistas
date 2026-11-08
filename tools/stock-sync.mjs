#!/usr/bin/env node
/**
 * Genera stock.json con el catalogo y el stock de KPI One.
 * Uso:
 *   KP_EMAIL=... KP_PASSWORD=... node tools/stock-sync.mjs --locales=J503,J502
 *   KP_EMAIL=... KP_PASSWORD=... node tools/stock-sync.mjs --all
 *   KP_EMAIL=... KP_PASSWORD=... node tools/stock-sync.mjs --locales-file=locales.txt
 */
const BASE = "https://kpione-app-production.up.railway.app";
const LIMITE = 500;

const args = new Map(
  process.argv.slice(2).map((a) => {
    const [k, ...v] = a.replace(/^--/, "").split("=");
    return [k, v.join("=") || "true"];
  }),
);

const email = process.env.KP_EMAIL ?? "";
const password = process.env.KP_PASSWORD ?? "";
const salida = args.get("out") ?? "stock.json";
const pausa = Number(args.get("delay") ?? 150);

// Marcas dadas de baja: no deben llegar al archivo. La app también las filtra
// (StockModels.MARCAS_EXCLUIDAS), pero así el JSON generado queda limpio.
// Se comparan sin espacios ni signos: "BERRY SUR" y "BERRYSUR" son la misma.
const MARCAS_EXCLUIDAS = new Set(["BERRYSUR"]);
const marcaExcluida = (marca) =>
  MARCAS_EXCLUIDAS.has(String(marca ?? "").toUpperCase().replace(/[^A-Z0-9]/g, ""));

if (args.has("help") || !email || !password) {
  console.log("Faltan credenciales: usa KP_EMAIL y KP_PASSWORD.");
  process.exit(1);
}

async function login() {
  const res = await fetch(BASE + "/api/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const t = await res.text();
    throw new Error("Login fallo (" + res.status + "): " + t.slice(0, 200));
  }
  const cookies = (res.headers.getSetCookie?.() ?? []).map((c) => c.split(";")[0]).join("; ");
  if (!cookies) throw new Error("El login no devolvio cookie");
  return cookies;
}

async function getJson(url, cookie) {
  const res = await fetch(url, { headers: { cookie } });
  const texto = await res.text();
  if (!res.ok) throw new Error(url + " -> " + res.status + " " + texto.slice(0, 120));
  if (texto.trimStart().startsWith("<")) throw new Error("Sesion invalida: vino HTML");
  return JSON.parse(texto);
}

async function catalogo(cookie) {
  const filtros = await getJson(BASE + "/api/b2b-reports/terreno/inventory-filter-options", cookie);
  const codigos = filtros.storeCodes ?? [];
  const marcas = [...(filtros.brands ?? [])].filter((m) => !marcaExcluida(m)).sort();
  const nombres = new Map();
  for (let offset = 0; offset < 4000; offset += LIMITE) {
    const pagina = await getJson(BASE + "/api/stores?limit=" + LIMITE + "&offset=" + offset, cookie);
    if (!Array.isArray(pagina) || pagina.length === 0) break;
    for (const s of pagina) if (s.storeCode) nombres.set(s.storeCode, s.name);
    if (pagina.length < LIMITE) break;
  }
  // Rutas del rutero (código KPI -> ["EMU-3", ...]).
  let rutas = {};
  try {
    const { readFileSync } = await import("node:fs");
    rutas = JSON.parse(readFileSync(new URL("./locales-rutas.json", import.meta.url), "utf8"));
  } catch (e) {
    console.warn("Aviso: sin tools/locales-rutas.json, el archivo no traerá rutas");
  }
  return {
    locales: codigos.map((code) => {
      const base = { c: code, n: nombres.get(code) ?? code };
      const rs = rutas[code];
      return rs && rs.length ? { ...base, r: rs } : base;
    }),
    marcas,
  };
}

async function stockDeLocal(code, cookie) {
  const filas = [];
  let offset = 0;
  let total = Infinity;
  while (offset < total) {
    const url = BASE + "/api/b2b-reports/terreno/inventory-browse?storeCodes=" +
      encodeURIComponent(code) + "&limit=" + LIMITE + "&offset=" + offset;
    const data = await getJson(url, cookie);
    const items = data.items ?? [];
    for (const it of items) {
      filas.push({
        s: it.store_code, n: it.store_name, b: it.brand_raw, p: it.product_desc_raw,
        k: it.sku_b2b_raw, u: it.stock_units, t: it.transit_units,
        d: it.snapshot_date, q: it.quiebre, i: it.days_of_inventory,
      });
    }
    total = data.pagination?.total ?? filas.length;
    if (items.length === 0) break;
    offset += LIMITE;
  }
  return filas;
}

async function controlDeCalidad(destino, nuevo) {
  const { readFileSync } = await import("node:fs");
  let previo;
  try {
    previo = JSON.parse(readFileSync(destino, "utf8"));
  } catch (e) {
    return { ok: true };
  }
  const filasPrev = (previo.items ?? []).length;
  const locPrev = (previo.locales ?? []).length;
  const filasNuevo = nuevo.items.length;
  const locNuevo = nuevo.locales.length;
  const caeFilas = filasPrev > 0 && filasNuevo < filasPrev * 0.9;
  const caeLocales = locPrev > 0 && locNuevo < locPrev * 0.9;
  return {
    ok: !(caeFilas || caeLocales),
    filasPrev,
    filasNuevo,
    locPrev,
    locNuevo,
  };
}

async function main() {
  const { readFileSync, writeFileSync } = await import("node:fs");
  console.log("Entrando a KPI One como " + email + "...");
  const cookie = await login();

  console.log("Descargando catalogo...");
  const { locales, marcas } = await catalogo(cookie);
  console.log("  " + locales.length + " locales, " + marcas.length + " marcas");

  let pedidos;
  if (args.has("all")) {
    pedidos = locales.map((l) => l.c);
  } else if (args.has("locales-file")) {
    pedidos = readFileSync(args.get("locales-file"), "utf8").split(/\s+/);
  } else if (args.get("locales")) {
    pedidos = args.get("locales").split(",");
  } else {
    throw new Error("Indica --locales=A,B  o  --locales-file=archivo  o  --all");
  }
  pedidos = [...new Set(pedidos.map((s) => s.trim().toUpperCase()).filter(Boolean))];

  const conocidos = new Set(locales.map((l) => l.c));
  const desconocidos = pedidos.filter((c) => !conocidos.has(c));
  if (desconocidos.length) console.warn("Aviso: fuera del catalogo: " + desconocidos.join(", "));

  const items = [];
  let i = 0;
  for (const code of pedidos) {
    i++;
    try {
      const filas = await stockDeLocal(code, cookie);
      items.push(...filas);
      if (i % 25 === 0 || i === pedidos.length) {
        console.log("  [" + i + "/" + pedidos.length + "] " + code + ": " + filas.length + " filas, total " + items.length);
      }
    } catch (e) {
      console.warn("  [" + i + "/" + pedidos.length + "] " + code + ": ERROR " + e.message);
    }
    if (pausa > 0) await new Promise((r) => setTimeout(r, pausa));
  }

  // El catalogo del archivo se limita a los locales pedidos: la app solo
  // muestra esos en el buscador.
  const pedidosSet = new Set(pedidos);
  const localesArchivo = locales.filter((l) => pedidosSet.has(l.c));
  const archivo = {
    locales: localesArchivo,
    marcas,
    items: items.filter((fila) => !marcaExcluida(fila.b)),
  };

  // Control de calidad: si el archivo nuevo cae más de 10 % respecto del
  // anterior, no se escribe nada (salvo --force).
  if (!args.has("force")) {
    const control = await controlDeCalidad(salida, archivo);
    if (!control.ok) {
      console.error("");
      console.error("Control de calidad: el archivo nuevo parece incompleto.");
      console.error("  filas: " + control.filasNuevo + " vs " + control.filasPrev + " antes");
      console.error("  locales: " + control.locNuevo + " vs " + control.locPrev + " antes");
      console.error("No se guardó nada. Si el cambio es intencional, repite con --force.");
      process.exit(2);
    }
  }

  const json = JSON.stringify(archivo);
  writeFileSync(salida, json);
  console.log("");
  console.log("Listo: " + salida);
  console.log("  locales: " + localesArchivo.length + " | marcas: " + marcas.length + " | filas: " + items.length);
  console.log("  tamano: " + (json.length / 1024 / 1024).toFixed(2) + " MB");
}

main().catch((e) => {
  console.error("Error: " + e.message);
  process.exit(1);
});
