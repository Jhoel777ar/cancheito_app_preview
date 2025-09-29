package com.example.myappcancheito.empleador.Nav_fragment_Empleador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.NotificationCompat
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
import java.util.Calendar
import kotlin.getValue
import kotlin.random.Random

class FragmentInicioV : Fragment(R.layout.fragment_inicio_v) {

    private var _b: FragmentInicioPBinding? = null
    private val b get() = _b!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val vm: OffersViewModel by viewModels()

    private lateinit var cargoAdapter: ArrayAdapter<String>
    private lateinit var ciudadAdapter: ArrayAdapter<String>
    private val cargos = mutableListOf<String>()
    private val ciudades = mutableListOf<String>()
    private var filtrosListos = false

    private lateinit var adapter: OffersUnifiedAdapter

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

        adapter = OffersUnifiedAdapter(
            onPostularClick = { offer -> intentarPostular(offer) }
        )
        b.rvOffers.layoutManager = LinearLayoutManager(context)
        b.rvOffers.adapter = adapter

        cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, cargos)
        ciudadAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ciudades)
        b.spCargo.adapter = cargoAdapter
        b.spCiudad.adapter = ciudadAdapter

        vm.items.observe(viewLifecycleOwner) { list -> adapter.submitList(list) }
        vm.empty.observe(viewLifecycleOwner) { isEmpty -> b.tvEmpty.isVisible = isEmpty }
        vm.cargos.observe(viewLifecycleOwner) {
            cargos.clear()
            cargos.addAll(it)
            cargoAdapter.notifyDataSetChanged()
        }
        vm.ciudades.observe(viewLifecycleOwner) {
            ciudades.clear()
            ciudades.addAll(it)
            ciudadAdapter.notifyDataSetChanged()
        }
        vm.filtrosListos.observe(viewLifecycleOwner) { ready ->
            filtrosListos = ready
            if (ready) aplicarFiltros()
        }
        vm.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null && isAdded) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
        vm.newOffer.observe(viewLifecycleOwner) { offer ->
            if (offer != null) showNotification(offer)
        }

        if (isNetworkAvailable()) {
            b.groupError.isVisible = false
            vm.loadAndListenOffers(db)
        } else {
            b.groupError.isVisible = true
            b.tvError.text = "No hay conexión a internet"
        }

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

        cargarPostulacionesDelUsuario()
    }

    override fun onDestroyView() {
        postListener?.let { l -> postQuery?.removeEventListener(l) }
        _b = null
        super.onDestroyView()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun simpleListener(block: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block()
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun filtroActual(): OffersFilter {
        val cargoSel = b.spCargo.selectedItem?.toString()?.takeIf { it != "Todos" }
        val ciudadSel = b.spCiudad.selectedItem?.toString()?.takeIf { it != "Todas" }
        val q = b.etSearch.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return OffersFilter(cargo = cargoSel, ciudad = ciudadSel, query = q)
    }

    private fun aplicarFiltros() {
        vm.applyFilters(filtroActual())
    }

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
            fechaPostulacion = System.currentTimeMillis()
        )
        db.child("postulaciones").child(key).setValue(p)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Postulación enviada con éxito", Toast.LENGTH_SHORT).show()
                cargarPostulacionesDelUsuario()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al postular: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun cargarPostulacionesDelUsuario() {
        val uid = auth.currentUser?.uid ?: return
        postQuery?.let { q -> postListener?.let { q.removeEventListener(it) } }

        postQuery = db.child("postulaciones").orderByChild("postulanteId").equalTo(uid)
        postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val applied: Set<String> = snapshot.children.mapNotNull {
                    it.getValue(Postulacion::class.java)?.offerId
                }.toSet()
                adapter.updateAppliedOffers(applied)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        postQuery!!.addValueEventListener(postListener as ValueEventListener)
    }

    private fun showNotification(offer: Offer) {
        val today = Calendar.getInstance()
        val offerDate = Calendar.getInstance().apply { timeInMillis = offer.createdAt ?: return }
        if (today.get(Calendar.YEAR) != offerDate.get(Calendar.YEAR) ||
            today.get(Calendar.DAY_OF_YEAR) != offerDate.get(Calendar.DAY_OF_YEAR)) {
            return
        }
        val context = requireContext()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "new_offer_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Offers", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.baseline_domain_verification_24)
            .setContentTitle("Nueva Oferta Disponible")
            .setContentText("Cargo: ${offer.cargo} en ${offer.ubicacion}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(Random.nextInt(), notification)
    }
}