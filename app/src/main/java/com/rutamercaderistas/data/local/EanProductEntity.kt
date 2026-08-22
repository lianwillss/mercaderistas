package com.rutamercaderistas.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ean_products",
    indices = [
        Index(value = ["eanPrincipal"]),
        Index(value = ["codCencosud"]),
        Index(value = ["codProveedor"]),
        Index(value = ["codigoBarra"]),
        Index(value = ["marca"]),
        Index(value = ["descripcion_norm"]),
        Index(value = ["marca_norm"]),
    ]
)
data class EanProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val codCencosud: String = "",
    val codProveedor: String = "",
    val eanPrincipal: String = "",
    val descripcionProducto: String = "",
    @ColumnInfo(name = "descripcion_norm") val descripcionNorm: String = "",
    val marca: String = "",
    @ColumnInfo(name = "marca_norm") val marcaNorm: String = "",
    val unBase: String = "",
    val unPedido: String = "",
    val conversion: String = "",
    val estado: String = "",
    val catN1Cencosud: String = "",
    val catN2Cencosud: String = "",
    val catN3Cencosud: String = "",
    val catN4Cencosud: String = "",
    val catN1Proveedor: String = "",
    val catN2Proveedor: String = "",
    val catN3Proveedor: String = "",
    val catN4Proveedor: String = "",
    val codigoBarra: String = "",
)