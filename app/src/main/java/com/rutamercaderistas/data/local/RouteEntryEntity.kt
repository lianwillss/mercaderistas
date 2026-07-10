package com.rutamercaderistas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rutamercaderistas.models.EntradaRuta

@Entity(
    tableName = "route_entries",
    indices = [Index("rutero")],
)
data class RouteEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rutero: String,
    val codigo: String,
    val local: String,
    val direccion: String,
    val cliente: String,
    val cadena: String,
    val formato: String,
    val region: String,
    val comuna: String,
    val supervisor: String,
    val gestores: String,
    val modalidad: String,
    val equipo: String,
    val reponedor: String,
    val lunes: Boolean,
    val martes: Boolean,
    val miercoles: Boolean,
    val jueves: Boolean,
    val viernes: Boolean,
    val sabado: Boolean,
    val domingo: Boolean,
)

fun List<EntradaRuta>.toEntities(): List<RouteEntryEntity> = map { it.toEntity() }

fun EntradaRuta.toEntity() = RouteEntryEntity(
    rutero = rutero,
    codigo = codigo,
    local = local,
    direccion = direccion,
    cliente = cliente,
    cadena = cadena,
    formato = formato,
    region = region,
    comuna = comuna,
    supervisor = supervisor,
    gestores = gestores,
    modalidad = modalidad,
    equipo = equipo,
    reponedor = reponedor,
    lunes = lunes,
    martes = martes,
    miercoles = miercoles,
    jueves = jueves,
    viernes = viernes,
    sabado = sabado,
    domingo = domingo,
)

fun List<RouteEntryEntity>.toDomain() = map { it.toDomain() }

fun RouteEntryEntity.toDomain() = EntradaRuta(
    rutero = rutero,
    codigo = codigo,
    local = local,
    direccion = direccion,
    cliente = cliente,
    cadena = cadena,
    formato = formato,
    region = region,
    comuna = comuna,
    supervisor = supervisor,
    gestores = gestores,
    modalidad = modalidad,
    equipo = equipo,
    reponedor = reponedor,
    lunes = lunes,
    martes = martes,
    miercoles = miercoles,
    jueves = jueves,
    viernes = viernes,
    sabado = sabado,
    domingo = domingo,
)
