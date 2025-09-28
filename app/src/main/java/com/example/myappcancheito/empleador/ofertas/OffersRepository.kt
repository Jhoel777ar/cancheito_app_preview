package com.example.myappcancheito.empleador.ofertas

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class OffersRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
) {
    private suspend fun readAll(): List<Offer> {
        val snap = db.child("ofertas").get().await()
        return snap.children.mapNotNull { it.getValue(Offer::class.java)?.copy(id = it.key ?: "") }
    }

    /** Sin filtros → recientes (solo ACTIVA) */
    suspend fun getRecent(limit: Int = 200): List<Offer> =
        readAll()
            .filter { it.estado.equals("ACTIVA", true) }
            .sortedByDescending { it.createdAt }
            .take(limit)

    /** Solo cargo */
    suspend fun getByCargo(cargo: String, limit: Int = 200): List<Offer> =
        readAll()
            .filter { it.estado.equals("ACTIVA", true) && it.cargo.equals(cargo, true) }
            .sortedByDescending { it.createdAt }
            .take(limit)

    /** Solo ciudad */
    suspend fun getByUbicacion(ciudad: String, limit: Int = 200): List<Offer> =
        readAll()
            .filter { it.estado.equals("ACTIVA", true) && it.ubicacion.equals(ciudad, true) }
            .sortedByDescending { it.createdAt }
            .take(limit)

    /** Cargo + ciudad */
    suspend fun getByCargoAndUbicacion(cargo: String, ciudad: String, limit: Int = 200): List<Offer> =
        readAll()
            .filter {
                it.estado.equals("ACTIVA", true) &&
                        it.cargo.equals(cargo, true) &&
                        it.ubicacion.equals(ciudad, true)
            }
            .sortedByDescending { it.createdAt }
            .take(limit)
}
