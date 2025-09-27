package com.example.myappcancheito.empleador.ofertas

data class Offer(
    var id: String = "",
    var employerId: String = "",
    var cargo: String = "",
    var descripcion: String = "",
    var modalidad: String = "",
    var ubicacion: String = "",
    var estado: String = "ACTIVA",
    var pago_aprox: String = "",
    var fecha_limite: Long? = null,
    var createdAt: Long = System.currentTimeMillis()
)