package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentInicioPBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import com.example.myappcancheito.postulante.aplicaciones.Postulacion
import com.example.myappcancheito.postulante.ui.OffersUnifiedAdapter
import com.example.myappcancheito.postulante.ui.OffersFilter
import com.example.myappcancheito.postulante.ui.OffersViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FragmentInicioP : Fragment(R.layout.fragment_inicio_p) {

    // ---- Binding ÚNICO ----
    private var _b: FragmentInicioPBinding? = null
    private val b get() = _b!!

    // ---- Firebase / VM ----
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val vm: OffersViewModel by viewModels()

    // ---- UI (spinners) ----
    private lateinit var cargoAdapter: ArrayAdapter<String>
    private lateinit var ciudadAdapter: ArrayAdapter<String>
    private val cargos = mutableListOf("Todos")
    private val ciudades = mutableListOf("Todas")
    private var filtrosListos = false

    // ---- Adapter Único ----
    private lateinit var adapter: OffersUnifiedAdapter

    // ---------------- LIFECYCLE ----------------
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentInicioPBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adapter (postular + click en item)
        adapter = OffersUnifiedAdapter(
            onPostularClick = { offer -> intentarPostular(offer) },
            onItemClick = { /* si quieres abrir detalle, hazlo aquí */ }
        )

        b.rvOffers.layoutManager = LinearLayoutManager(requireContext())
        b.rvOffers.adapter = adapter

        // Spinners
        cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, cargos)
        ciudadAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ciudades)
        b.spCargo.adapter = cargoAdapter
        b.spCiudad.adapter = ciudadAdapter

        // Observers del VM (lista y vacío)
        vm.items.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
        vm.empty.observe(viewLifecycleOwner) { isEmpty ->
            b.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }

        // Cargar opciones de filtros y primera lista
        if (isNetworkAvailable()) {
            b.groupError.isVisible = false
            cargarFiltrosDesdeFirebase()
        } else {
            b.groupError.isVisible = true
            b.tvError.text = "No hay conexión a internet"
        }

        // Acciones de filtros
        fun aplicar() {
            if (!filtrosListos) return
            val cargo = b.spCargo.selectedItem?.toString()?.takeIf { it != "Todos" }
            val ciudad = b.spCiudad.selectedItem?.toString()?.takeIf { it != "Todas" }
            vm.applyFilters(OffersFilter(cargo, ciudad))
        }

        b.spCargo.onItemSelectedListener = simpleListener { aplicar() }
        b.spCiudad.onItemSelectedListener = simpleListener { aplicar() }
        b.btnLimpiar.setOnClickListener {
            b.spCargo.setSelection(0)
            b.spCiudad.setSelection(0)
            if (filtrosListos) vm.clearFilters()
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    // ---------------- HELPERS ----------------
    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Lee cargos y ciudades de /ofertas y repuebla Spinners; luego pide al VM la lista inicial (recientes). */
    private fun cargarFiltrosDesdeFirebase() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) { db.child("ofertas").get().await() }
                val offers = snap.children.mapNotNull { it.getValue(Offer::class.java) }

                val cargosUnicos = offers.mapNotNull { it.cargo?.trim()?.takeIf { it.isNotEmpty() } }
                    .toSet().toList().sorted()
                val ciudadesUnicas = offers.mapNotNull { it.ubicacion?.trim()?.takeIf { it.isNotEmpty() } }
                    .toSet().toList().sorted()

                cargos.apply { clear(); add("Todos"); addAll(cargosUnicos) }
                ciudades.apply { clear(); add("Todas"); addAll(ciudadesUnicas) }
                cargoAdapter.notifyDataSetChanged()
                ciudadAdapter.notifyDataSetChanged()

                filtrosListos = true
                vm.loadInitial() // mostrar recientes
            } catch (e: Exception) {
                filtrosListos = false
                Toast.makeText(requireContext(), "Error cargando filtros: ${e.message}", Toast.LENGTH_SHORT).show()
                vm.loadInitial()
            }
        }
    }

    private fun simpleListener(block: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block()
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    // ---------------- POSTULAR ----------------
    private fun intentarPostular(oferta: Offer) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Inicia sesión para postular", Toast.LENGTH_SHORT).show()
            return
        }
        val ahora = System.currentTimeMillis()
        oferta.fecha_limite?.let { if (ahora > it) {
            Toast.makeText(requireContext(), "La oferta ya venció", Toast.LENGTH_SHORT).show()
            return
        } }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Postular")
            .setMessage("¿Deseas postular a esta oferta?")
            .setPositiveButton("Sí") { _, _ -> verificarPostulacionExistente(oferta, uid) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun verificarPostulacionExistente(oferta: Offer, uid: String) {
        val clave = "${oferta.id}_$uid"
        val ref = db.child("postulaciones").child(clave)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                Toast.makeText(requireContext(), "Ya postulaste a esta oferta", Toast.LENGTH_SHORT).show()
            } else {
                guardarPostulacion(oferta, uid)
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error al verificar: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guardarPostulacion(oferta: Offer, uid: String) {
        val clave = "${oferta.id}_$uid"
        val p = Postulacion(
            id = clave,
            offerId = oferta.id,
            postulanteId = uid,
            offerId_postulanteId = clave,
            fechaPostulacion = System.currentTimeMillis()
        )
        db.child("postulaciones").child(clave).setValue(p)
            .addOnSuccessListener { Toast.makeText(requireContext(), "Postulación enviada", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(requireContext(), "Error al postular: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
