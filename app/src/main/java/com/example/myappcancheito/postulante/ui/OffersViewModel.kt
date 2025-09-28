package com.example.myappcancheito.postulante.ui

import androidx.lifecycle.*
import com.example.myappcancheito.empleador.ofertas.OffersRepository
import com.example.myappcancheito.empleador.ofertas.Offer
import kotlinx.coroutines.launch

// ➜ Incluye query
data class OffersFilter(
    val cargo: String? = null,
    val ciudad: String? = null,
    val query: String? = null
)

class OffersViewModel(
    private val repo: OffersRepository = OffersRepository()
) : ViewModel() {

    private val _items = MutableLiveData<List<Offer>>(emptyList())
    val items: LiveData<List<Offer>> = _items

    private val _empty = MutableLiveData(false)
    val empty: LiveData<Boolean> = _empty

    fun loadInitial() = applyFilters(OffersFilter())

    fun applyFilters(filter: OffersFilter) {
        viewModelScope.launch {
            // 1) Trae base según cargo/ciudad
            val base: List<Offer> = when {
                filter.cargo.isNullOrBlank() && filter.ciudad.isNullOrBlank() ->
                    repo.getRecent()
                !filter.cargo.isNullOrBlank() && !filter.ciudad.isNullOrBlank() ->
                    repo.getByCargoAndUbicacion(filter.cargo!!, filter.ciudad!!)
                !filter.cargo.isNullOrBlank() ->
                    repo.getByCargo(filter.cargo!!)
                !filter.ciudad.isNullOrBlank() ->
                    repo.getByUbicacion(filter.ciudad!!)
                else ->
                    repo.getRecent()
            }

            // 2) Aplica búsqueda local por texto (query) + seguridad por si luego amplías filtros
            val filtered = base.filter { matchesFilter(it, filter) }

            _items.value = filtered
            _empty.value = filtered.isEmpty()
        }
    }

    fun clearFilters() = loadInitial()

    private fun matchesFilter(offer: Offer, f: OffersFilter): Boolean {
        val okCargo = f.cargo?.let { it.equals(offer.cargo, ignoreCase = true) } ?: true
        val okCiudad = f.ciudad?.let { it.equals(offer.ubicacion, ignoreCase = true) } ?: true
        val okQuery = f.query?.let { q ->
            val haystack = listOf(
                offer.cargo,
                offer.descripcion,
                offer.ubicacion,
                offer.modalidad,
                offer.pago_aprox
            ).joinToString(" ").lowercase()
            q.lowercase() in haystack
        } ?: true
        return okCargo && okCiudad && okQuery
    }
}
