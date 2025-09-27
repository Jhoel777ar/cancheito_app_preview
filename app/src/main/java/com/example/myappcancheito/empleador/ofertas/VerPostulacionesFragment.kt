package com.example.myappcancheito.empleador.ofertas

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentVerPostulacionesBinding
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VerPostulacionesFragment : Fragment(R.layout.fragment_ver_postulaciones) {

    private var _binding: FragmentVerPostulacionesBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var offerId: String? = null
    private var postulacionesListener: ValueEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVerPostulacionesBinding.bind(view)

        offerId = arguments?.getString("offerId")
        if (offerId == null) {
            Toast.makeText(requireContext(), "Oferta no encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        cargarPostulaciones()
    }

    private fun cargarPostulaciones() {
        val ref = db.child("postulaciones").orderByChild("offerId").equalTo(offerId)
        postulacionesListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.llPostulaciones.removeAllViews()

                if (!snapshot.exists()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "No hay postulantes para esta oferta"
                        textSize = 16f
                        setPadding(16, 16, 16, 16)
                    }
                    binding.llPostulaciones.addView(tvEmpty)
                    return
                }

                snapshot.children.mapNotNull { it.getValue(Postulacion::class.java) }
                    .sortedByDescending { it.fechaPostulacion }
                    .forEach { postulacion ->
                        val llItem = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(0, 0, 0, 8)
                        }

                        val tvPostulante = TextView(requireContext()).apply {
                            text = "Postulante: ${postulacion.postulanteId}"
                            textSize = 14f
                        }
                        llItem.addView(tvPostulante)

                        val tvFecha = TextView(requireContext()).apply {
                            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            text = "Fecha: ${dateFormat.format(Date(postulacion.fechaPostulacion))}"
                            textSize = 14f
                        }
                        llItem.addView(tvFecha)

                        val separator = View(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1
                            ).apply { setMargins(0, 8, 0, 8) }
                            setBackgroundColor(android.graphics.Color.GRAY)
                        }
                        llItem.addView(separator)

                        binding.llPostulaciones.addView(llItem)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        postulacionesListener?.let { db.child("postulaciones").removeEventListener(it) }
        _binding = null
        super.onDestroyView()
    }
}