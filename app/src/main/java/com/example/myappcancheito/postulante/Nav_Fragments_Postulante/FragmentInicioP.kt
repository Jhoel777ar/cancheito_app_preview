package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentInicioPBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import com.example.myappcancheito.postulante.aplicaciones.Postulacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class FragmentInicioP : Fragment(R.layout.fragment_inicio_p) {

    private var _binding: FragmentInicioPBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var listener: ValueEventListener? = null
    private val ofertas = mutableListOf<Offer>()
    private lateinit var adapter: OfertaAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInicioPBinding.bind(view)

        adapter = OfertaAdapter(ofertas) { oferta ->
            intentarPostular(oferta)
        }

        binding.rvOfertas.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOfertas.adapter = adapter

        checkNetworkAndLoadOfertas()
    }

    private fun checkNetworkAndLoadOfertas() {
        if (isNetworkAvailable()) {
            binding.tvError.isVisible = false
            binding.rvOfertas.isVisible = true
            cargarOfertas()
        } else {
            binding.tvError.text = "No hay conexión a internet"
            binding.tvError.isVisible = true
            binding.rvOfertas.isVisible = false
            Toast.makeText(requireContext(), "No hay conexión a internet", Toast.LENGTH_LONG).show()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun cargarOfertas() {
        val ref = db.child("ofertas").orderByChild("estado").equalTo("ACTIVA")
        listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ofertas.clear()
                val nuevasOfertas = snapshot.children.mapNotNull { it.getValue(Offer::class.java) }
                    .filter { it.estado == "ACTIVA" }
                    .sortedByDescending { it.createdAt }
                if (nuevasOfertas.isEmpty()) {
                    binding.tvError.text = "No hay ofertas activas"
                    binding.tvError.isVisible = true
                    binding.rvOfertas.isVisible = false
                } else {
                    binding.tvError.isVisible = false
                    binding.rvOfertas.isVisible = true
                    ofertas.addAll(nuevasOfertas)
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.tvError.text = "Error al cargar ofertas: ${error.message}"
                binding.tvError.isVisible = true
                binding.rvOfertas.isVisible = false
                Toast.makeText(requireContext(), "Error al cargar ofertas: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
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
            .setPositiveButton("Sí") { _, _ ->
                verificarPostulacionExistente(oferta, uid)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun verificarPostulacionExistente(oferta: Offer, uid: String) {
        val clave = "${oferta.id}_$uid"
        val ref = db.child("postulaciones").child(clave)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(requireContext(), "Ya postulaste a esta oferta", Toast.LENGTH_SHORT).show()
                } else {
                    guardarPostulacion(oferta, uid)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error al verificar postulaciones: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun guardarPostulacion(oferta: Offer, uid: String) {
        val clave = "${oferta.id}_$uid"

        val postulacion = Postulacion(
            id = clave,
            offerId = oferta.id,
            postulanteId = uid,
            offerId_postulanteId = clave,
            fechaPostulacion = System.currentTimeMillis()
        )

        db.child("postulaciones").child(clave).setValue(postulacion)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Postulación enviada con éxito", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al postular: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        listener?.let { db.child("ofertas").removeEventListener(it) }
        _binding = null
        super.onDestroyView()
    }
}