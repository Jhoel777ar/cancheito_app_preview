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

class VerPostulacionesFragment : Fragment(R.layout.fragment_ver_postulaciones) {

    private var _binding: FragmentVerPostulacionesBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var offerId: String? = null

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
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
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
                    .forEach { postulacion ->
                        val tvItem = TextView(requireContext()).apply {
                            text = "Postulante: ${postulacion.postulanteId} \nFecha: ${postulacion.fechaPostulacion}"
                            textSize = 14f
                            setPadding(16, 16, 16, 16)
                        }
                        binding.llPostulaciones.addView(tvItem)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
