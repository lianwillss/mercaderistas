package com.rutamercaderistas.data.stock

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Almacenamiento del snapshot de stock; permite tests con un mapa en memoria. */
interface StockStorage {
    fun read(name: String): String?
    fun writeAtomic(name: String, text: String): Boolean
    fun backup(name: String)
    fun exists(name: String): Boolean
    fun delete(name: String)
}

/**
 * Implementación real: escribe a un temporal, hace fsync y reemplaza el
 * archivo con un move atómico. Así una descarga cortada nunca deja el
 * snapshot bueno a medio escribir.
 */
class FileStockStorage(private val root: File) : StockStorage {

    override fun read(name: String): String? = runCatching {
        val archivo = File(root, name)
        if (archivo.exists()) archivo.readText() else null
    }.getOrNull()

    override fun writeAtomic(name: String, text: String): Boolean = runCatching {
        root.mkdirs()
        val destino = File(root, name)
        val temporal = File(root, name + ".tmp")
        FileOutputStream(temporal).use { salida ->
            salida.write(text.toByteArray(Charsets.UTF_8))
            salida.fd.sync()
        }
        try {
            Files.move(
                temporal.toPath(),
                destino.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                temporal.toPath(),
                destino.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        true
    }.getOrDefault(false)

    override fun backup(name: String) {
        runCatching {
            val origen = File(root, name)
            if (origen.exists()) {
                origen.copyTo(File(root, name + ".bak"), overwrite = true)
            }
        }
    }

    override fun exists(name: String): Boolean = File(root, name).exists()

    override fun delete(name: String) {
        runCatching { File(root, name).delete() }
    }
}
