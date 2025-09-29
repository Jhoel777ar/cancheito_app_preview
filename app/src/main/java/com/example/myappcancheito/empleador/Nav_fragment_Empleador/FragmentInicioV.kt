package com.example.myappcancheito.empleador.Nav_fragment_Empleador

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentInicioPBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import com.example.myappcancheito.postulante.aplicaciones.Postulacion
import com.example.myappcancheito.postulante.ui.OffersFilter
import com.example.myappcancheito.postulante.ui.OffersUnifiedAdapter
import com.example.myappcancheito.postulante.ui.OffersViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.getValue

class FragmentInicioV : Fragment(R.layout.fragment_inicio_p) {

    // ViewBinding
    private var _b: FragmentInicioPBinding? = null
    private val b get() = _b!!

    // Firebase + VM
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val vm: OffersViewModel by viewModels()

    // Spinners
    private lateinit var cargoAdapter: ArrayAdapter<String>
    private lateinit var ciudadAdapter: ArrayAdapter<String>
    private val cargos = mutableListOf("Todos")
    private val ciudades = mutableListOf("Todas")
    private var filtrosListos = false

    // Recycler
    private lateinit var adapter: OffersUnifiedAdapter

    // Listener de postulaciones (para “Ya postulaste”)
    private var postQuery: Query? = null
    private var postListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentInicioPBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adapter (ListAdapter + DiffUtil)
        adapter = OffersUnifiedAdapter(
            onPostularClick = { offer -> intentarPostular(offer) },
            onItemClick = { /* opcional: abrir detalle */ }
        )
        b.rvOffers.layoutManager = LinearLayoutManager(requireContext())
        b.rvOffers.adapter = adapter

        // Spinners
        cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, cargos)
        ciudadAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ciudades)
        b.spCargo.adapter = cargoAdapter
        b.spCiudad.adapter = ciudadAdapter

        // Observers del ViewModel
        vm.items.observe(viewLifecycleOwner) { list -> adapter.submitList(list) }
        vm.empty.observe(viewLifecycleOwner) { isEmpty -> b.tvEmpty.isVisible = isEmpty }

        // Carga inicial
        if (isNetworkAvailable()) {
            b.groupError.isVisible = false
            cargarFiltrosDesdeFirebase()
        } else {
            b.groupError.isVisible = true
            b.tvError.text = "No hay conexión a internet"
        }

        // Listeners de filtros/búsqueda
        val aplicar: () -> Unit = {
            if (filtrosListos) vm.applyFilters(filtroActual())
        }
        b.spCargo.onItemSelectedListener = simpleListener { aplicar() }
        b.spCiudad.onItemSelectedListener = simpleListener { aplicar() }
        b.etSearch.addTextChangedListener { aplicar() }

        b.btnLimpiar.setOnClickListener {
            b.spCargo.setSelection(0)
            b.spCiudad.setSelection(0)
            b.etSearch.setText("")
            if (filtrosListos) vm.clearFilters()
        }

        // Escucha las postulaciones del usuario para actualizar botones
        cargarPostulacionesDelUsuario()
    }

    override fun onDestroyView() {
        // Limpia listener de postulaciones
        postListener?.let { l -> postQuery?.removeEventListener(l) }
        _b = null
        super.onDestroyView()
    }

    // ----------------- Helpers -----------------

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Lee /ofertas para poblar los spinners y luego pide recientes al VM. */
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
                vm.loadInitial()
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

    /** Construye el filtro actual (cargo, ciudad, query de la lupita). */
    private fun filtroActual(): OffersFilter {
        val cargoSel = b.spCargo.selectedItem?.toString()?.takeIf { it != "Todos" }
        val ciudadSel = b.spCiudad.selectedItem?.toString()?.takeIf { it != "Todas" }
        val q = b.etSearch.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return OffersFilter(cargo = cargoSel, ciudad = ciudadSel, query = q)
    }

    // --------------- Postular ---------------

    private fun intentarPostular(oferta: Offer) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Inicia sesión para postular", Toast.LENGTH_SHORT).show()
            return
        }
        val ahora = System.currentTimeMillis()
        oferta.fecha_limite?.let {
            if (ahora > it) {
                Toast.makeText(requireContext(), "La oferta ya venció", Toast.LENGTH_SHORT).show()
                return
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Postular")
            .setMessage("¿Deseas postular a esta oferta?")
            .setPositiveButton("Sí") { _, _ -> verificarPostulacionExistente(oferta, uid) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun verificarPostulacionExistente(oferta: Offer, uid: String) {
        val key = "${oferta.id}_$uid"
        db.child("postulaciones").child(key).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    Toast.makeText(requireContext(), "Ya postulaste a esta oferta", Toast.LENGTH_SHORT).show()
                } else {
                    guardarPostulacion(oferta, uid)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al verificar: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun guardarPostulacion(oferta: Offer, uid: String) {
        val key = "${oferta.id}_$uid"
        val p = Postulacion(
            id = key,
            offerId = oferta.id,
            postulanteId = uid,
            offerId_postulanteId = key,
            // si tu data class tiene 'estado', puedes guardar:
            // estado = "pendiente",
            fechaPostulacion = System.currentTimeMillis()
        )
        db.child("postulaciones").child(key).setValue(p)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Postulación enviada con éxito", Toast.LENGTH_SHORT).show()
                // Refresca inmediatamente el estado del botón
                cargarPostulacionesDelUsuario()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al postular: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // --------------- Applied offers -> deshabilitar botón ---------------

    private fun cargarPostulacionesDelUsuario() {
        val uid = auth.currentUser?.uid ?: return
        postQuery?.let { q -> postListener?.let { q.removeEventListener(it) } } // por si se re-llama

        postQuery = db.child("postulaciones").orderByChild("postulanteId").equalTo(uid)
        postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val applied: Set<String> = snapshot.children.mapNotNull {
                    it.getValue(Postulacion::class.java)?.offerId
                }.toSet()
                adapter.updateAppliedOffers(applied) // ← esto cambia el texto del botón
            }
            override fun onCancelled(error: DatabaseError) { /* opcional: log */ }
        }
        postQuery!!.addValueEventListener(postListener as ValueEventListener)
    }
}