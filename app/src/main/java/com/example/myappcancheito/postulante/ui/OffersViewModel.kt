package com.example.myappcancheito.postulante.ui

import androidx.lifecycle.*
import com.example.myappcancheito.empleador.ofertas.Offer
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class OffersFilter(
    val cargo: String? = null,
    val ciudad: String? = null,
    val query: String? = null
)

class OffersViewModel : ViewModel() {

    private val allOffers = mutableListOf<Offer>()
    private var currentFilter = OffersFilter()

    private val _items = MutableLiveData<List<Offer>>(emptyList())
    val items: LiveData<List<Offer>> = _items

    private val _empty = MutableLiveData(false)
    val empty: LiveData<Boolean> = _empty

    private val _cargos = MutableLiveData<List<String>>(listOf("Todos"))
    val cargos: LiveData<List<String>> = _cargos

    private val _ciudades = MutableLiveData<List<String>>(listOf("Todas"))
    val ciudades: LiveData<List<String>> = _ciudades

    private val _filtrosListos = MutableLiveData(false)
    val filtrosListos: LiveData<Boolean> = _filtrosListos

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _newOffer = MutableLiveData<Offer?>()
    val newOffer: LiveData<Offer?> = _newOffer

    private var offersListener: ChildEventListener? = null
    private var dbRef: DatabaseReference? = null

    fun loadAndListenOffers(db: DatabaseReference) {
        dbRef = db
        viewModelScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) { db.child("ofertas").get().await() }
                val offers = snap.children.mapNotNull { it.getValue(Offer::class.java) }.filter { it.estado == "ACTIVA" }
                setAllOffers(offers)
                updateFiltros(offers)
                _filtrosListos.value = true
                loadInitial()
            } catch (e: Exception) {
                _error.value = "Error cargando filtros: ${e.message}"
                _filtrosListos.value = false
                loadInitial()
            }
        }

        offersListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val offer = snapshot.getValue(Offer::class.java) ?: return
                if (offer.estado == "ACTIVA") {
                    addOffer(offer)
                    _newOffer.value = offer
                    _newOffer.value = null // Reset
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val offer = snapshot.getValue(Offer::class.java) ?: return
                if (offer.estado == "ACTIVA") updateOffer(offer) else removeOffer(offer.id)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val offer = snapshot.getValue(Offer::class.java) ?: return
                removeOffer(offer.id)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("ofertas").addChildEventListener(offersListener!!)
    }

    private fun updateFiltros(offers: List<Offer>) {
        val cargosUnicos = offers.mapNotNull { it.cargo?.trim()?.takeIf { it.isNotEmpty() } }.toSet().sorted()
        val ciudadesUnicas = offers.mapNotNull { it.ubicacion?.trim()?.takeIf { it.isNotEmpty() } }.toSet().sorted()
        _cargos.value = listOf("Todos") + cargosUnicos
        _ciudades.value = listOf("Todas") + ciudadesUnicas
    }

    fun setAllOffers(list: List<Offer>) {
        allOffers.clear()
        allOffers.addAll(list)
        applyFilters(currentFilter)
    }

    fun addOffer(offer: Offer) {
        if (allOffers.none { it.id == offer.id }) {
            allOffers.add(offer)
            updateFiltros(allOffers)
        }
        applyFilters(currentFilter)
    }

    fun updateOffer(offer: Offer) {
        val index = allOffers.indexOfFirst { it.id == offer.id }
        if (index != -1) {
            allOffers[index] = offer
            updateFiltros(allOffers)
        }
        applyFilters(currentFilter)
    }

    fun removeOffer(id: String) {
        allOffers.removeIf { it.id == id }
        updateFiltros(allOffers)
        applyFilters(currentFilter)
    }

    fun loadInitial() = applyFilters(OffersFilter())

    fun applyFilters(filter: OffersFilter) {
        currentFilter = filter
        viewModelScope.launch {
            val filtered = allOffers.filter { matchesFilter(it, filter) }
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

    override fun onCleared() {
        offersListener?.let { dbRef?.child("ofertas")?.removeEventListener(it) }
        super.onCleared()
    }
}