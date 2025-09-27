package com.example.myappcancheito.empleador.ofertas

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentMisOfertasBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MisOfertasFragment : Fragment(R.layout.fragment_mis_ofertas) {

    private var _binding: FragmentMisOfertasBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var listener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMisOfertasBinding.bind(view)
        cargar()
    }

    private fun cargar() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
            return
        }
        val ref = db.child("ofertas").orderByChild("employerId").equalTo(uid)
        listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.llOfertas.removeAllViews() // Clear previous views
                val items = snapshot.children.mapNotNull { it.getValue(Offer::class.java) }
                    .filter { it.estado == "ACTIVA" }
                    .sortedByDescending { it.createdAt }

                if (items.isEmpty()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "No hay ofertas activas"
                        textSize = 16f
                        setPadding(16, 16, 16, 16)
                    }
                    binding.llOfertas.addView(tvEmpty)
                    return
                }

                items.forEach { offer ->
                    val llItem = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 16, 16, 16)
                    }

                    val tvCargo = TextView(requireContext()).apply {
                        text = "Cargo: ${offer.cargo}"
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvCargo)

                    val tvDescripcion = TextView(requireContext()).apply {
                        text = "Descripción: ${offer.descripcion}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvDescripcion)

                    val tvModalidad = TextView(requireContext()).apply {
                        text = "Modalidad: ${offer.modalidad}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvModalidad)

                    val tvUbicacion = TextView(requireContext()).apply {
                        text = "Ubicación: ${offer.ubicacion}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvUbicacion)

                    val tvPagoAprox = TextView(requireContext()).apply {
                        text = "Pago aproximado: ${offer.pago_aprox}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvPagoAprox)

                    val tvFechaLimite = TextView(requireContext()).apply {
                        text = "Fecha límite: ${offer.fecha_limite?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "No especificada"}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvFechaLimite)

                    val tvEstado = TextView(requireContext()).apply {
                        text = "Estado: ${offer.estado}"
                        textSize = 14f
                        setPadding(0, 0, 0, 8)
                    }
                    llItem.addView(tvEstado)

                    val tvCreatedAt = TextView(requireContext()).apply {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        text = "Creada el: ${dateFormat.format(Date(offer.createdAt))}"
                        textSize = 14f
                        setPadding(0, 0, 0, 16)
                    }
                    llItem.addView(tvCreatedAt)

                    val separator = View(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply { setMargins(0, 8, 0, 8) }
                        setBackgroundColor(android.graphics.Color.GRAY)
                    }
                    llItem.addView(separator)

                    binding.llOfertas.addView(llItem)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error al cargar ofertas: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        listener?.let { db.child("ofertas").removeEventListener(it) }
        _binding = null
        super.onDestroyView()
    }
}