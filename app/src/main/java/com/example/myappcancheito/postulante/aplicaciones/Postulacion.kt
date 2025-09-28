package com.example.myappcancheito.postulante.aplicaciones

data class Postulacion(
    var id: String = "",
    var offerId: String = "",
    var postulanteId: String = "",
    var offerId_postulanteId: String = "",
    var estado: String = "pendiente",   // ← usa 'estado'
    var fechaPostulacion: Long = 0L
)

