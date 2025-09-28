package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentMisPostulacionesBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import com.example.myappcancheito.postulante.aplicaciones.Postulacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MisPostulacionesFragment : Fragment(R.layout.fragment_mis_postulaciones) {

    private var _binding: FragmentMisPostulacionesBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var adapter: MisPostulacionesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMisPostulacionesBinding.bind(view)

        adapter = MisPostulacionesAdapter()
        binding.rvPostulaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPostulaciones.adapter = adapter

        cargarMisPostulaciones()
    }

    private fun cargarMisPostulaciones() {
        val uid = auth.currentUser?.uid ?: run {
            mostrarEstado(empty = true)
            return
        }

        mostrarCargando(true)

        // 1) Traer postulaciones del usuario
        val postRef = db.child("postulaciones")
        val q = postRef.orderByChild("postulanteId").equalTo(uid)

        q.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val postulaciones = snap.children.mapNotNull { it.getValue(Postulacion::class.java) }

                if (postulaciones.isEmpty()) {
                    adapter.submit(emptyList())
                    mostrarEstado(empty = true)
                    return
                }

                // 2) Recolectar offerIds
                val offerIds = postulaciones.mapNotNull { it.offerId }.toSet()
                db.child("ofertas").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(offSnap: DataSnapshot) {
                        // 3) Armar mapa offerId -> Offer
                        val offersMap = mutableMapOf<String, Offer>()
                        for (id in offerIds) {
                            offSnap.child(id).getValue(Offer::class.java)?.let { offersMap[id] = it }
                        }

                        // 4) Construir UI list (cargo + estado)
                        val ui = postulaciones.map { p ->
                            val cargo = offersMap[p.offerId]?.cargo ?: "(Oferta no encontrada)"
                            PostulacionUI(
                                cargo = cargo,
                                estado = p.estado_postulacion.ifBlank { "pendiente" },
                                fechaPostulacion = p.fechaPostulacion
                            )
                        }

                        adapter.submit(ui)
                        mostrarEstado(empty = ui.isEmpty())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        adapter.submit(emptyList())
                        mostrarEstado(empty = true)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                adapter.submit(emptyList())
                mostrarEstado(empty = true)
            }
        })
    }

    private fun mostrarCargando(show: Boolean) {
        binding.progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun mostrarEstado(empty: Boolean) {
        mostrarCargando(false)
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvPostulaciones.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
