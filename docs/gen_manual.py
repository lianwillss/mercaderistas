from fpdf import FPDF
import os

pdf = FPDF()
pdf.set_auto_page_break(auto=True, margin=20)

font_path = "/Library/Fonts/Arial Unicode.ttf"
bold_path = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"

print(f"Using font: {font_path}")
pdf.add_font("Uni", "", font_path)
pdf.add_font("Uni", "B", bold_path)

# Title page
pdf.add_page()
pdf.set_font("Uni", "B", 26)
pdf.ln(60)
pdf.cell(0, 14, "Mercaderistas", align="C", new_x="LMARGIN", new_y="NEXT")
pdf.set_font("Uni", "", 14)
pdf.cell(0, 10, "Manual de Usuario", align="C", new_x="LMARGIN", new_y="NEXT")
pdf.ln(40)
pdf.set_font("Uni", "", 10)
pdf.cell(0, 6, "Version 1.0", align="C", new_x="LMARGIN", new_y="NEXT")

sections = [
    ("1. Sincronizar datos", [
        "La aplicacion descarga la informacion desde Google Drive automaticamente.",
        "",
        "Sincronizacion manual:",
        "  1. Toca el boton [...] (tres puntos) en la esquina superior derecha.",
        "  2. Selecciona \"Forzar sincronizacion\".",
        "  3. Una barra de progreso indica que los datos se estan descargando.",
        "  4. Al terminar, la lista de rutas se actualiza.",
        "",
        "La app funciona sin conexion despues de la primera sincronizacion.",
    ]),
    ("2. Seleccionar una ruta", [
        "  1. Toca el campo \"Buscar ruta...\" en la parte superior.",
        "  2. Escribe el nombre de la ruta (ej. \"RUTA 1\").",
        "  3. Selecciona la ruta de la lista desplegable.",
        "  4. Tambien puedes elegir una ruta reciente del historial.",
    ]),
    ("3. Navegar entre dias", [
        "- Usa el selector de dias (LUN, MAR, MIE, etc.) para cambiar de dia.",
        "- Tambien puedes deslizar horizontalmente sobre la pantalla.",
        "- Los dias sin visitas no aparecen en el selector.",
    ]),
    ("4. Ver locales y marcas", [
        "Cada local se muestra como una tarjeta blanca con icono de color segun la cadena:",
        "  Verde   = Jumbo",
        "  Azul    = Lider",
        "  Fucsia  = Santa Isabel / SISA",
        "  Rojo    = Unimarc",
        "  Amarillo = Tottus",
        "  Morado  = Alvi",
        "",
        "La tarjeta incluye: nombre, codigo interno, direccion (toca para abrir Maps)",
        "y un contador de marcas para visitar.",
        "",
        "Marcas prioritarias: tarjeta blanca con barra naranja y estrella.",
        "Marcas normales: fila compacta con circulo de iniciales en color.",
    ]),
    ("5. Abrir PDF de una marca", [
        "  1. Toca cualquier marca dentro de un local.",
        "  2. La app abre el PDF del catalogador en la pagina exacta de esa marca.",
        "  3. Si la marca no se encuentra, aparece un mensaje: \"Marca no encontrada\".",
    ]),
    ("6. Ver todos los locales", [
        "  1. Toca la tarjeta \"Locales\" en la seccion de estadisticas.",
        "  2. Se abre una pantalla completa con todos los locales de la ruta.",
        "  3. Toca la direccion para abrirla en Google Maps.",
        "  4. Presiona <- para volver.",
    ]),
]

for title, lines in sections:
    pdf.add_page()
    pdf.set_font("Uni", "B", 16)
    pdf.cell(0, 12, title, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)
    pdf.set_font("Uni", "", 11)
    for line in lines:
        if line == "":
            pdf.ln(3)
        elif line.startswith("  "):
            pdf.set_x(24)
            pdf.multi_cell(pdf.w - 32, 6, line.strip())
        else:
            pdf.set_x(16)
            pdf.multi_cell(pdf.w - 32, 6, line)

pdf.output("/Users/will/Desktop/MANUAL.pdf")
print("PDF generado: ~/Desktop/MANUAL.pdf")
