package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
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
    private var postulacionesListener: ValueEventListener? = null
    private var postulacionesQuery: Query? = null

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

        postulacionesQuery?.removeEventListener(postulacionesListener ?: return)

        postulacionesQuery = db.child("postulaciones").orderByChild("postulanteId").equalTo(uid)
        postulacionesListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                if (!isAdded) return

                val postulaciones = snap.children.mapNotNull { it.getValue(Postulacion::class.java) }

                if (postulaciones.isEmpty()) {
                    adapter.submit(emptyList())
                    mostrarEstado(empty = true)
                    return
                }

                val offerIds = postulaciones.mapNotNull { it.offerId }.toSet()

                db.child("ofertas").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(offSnap: DataSnapshot) {
                        if (!isAdded) return

                        val offersMap = mutableMapOf<String, Offer>()
                        for (id in offerIds) {
                            offSnap.child(id).getValue(Offer::class.java)?.let { offersMap[id] = it }
                        }

                        val employerIds = offersMap.values.mapNotNull { it.employerId }.toSet()
                        db.child("Usuarios").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnap: DataSnapshot) {
                                if (!isAdded) return

                                val employersMap = mutableMapOf<String, String>()
                                for (id in employerIds) {
                                    val user = userSnap.child(id)
                                    val name = user.child("nombre_completo").getValue(String::class.java)
                                    val email = user.child("email").getValue(String::class.java)
                                    employersMap[id] = name ?: email ?: "Empleador desconocido"
                                }

                                val ui = postulaciones.map { p ->
                                    val offer = offersMap[p.offerId]
                                    PostulacionUI(
                                        cargo = offer?.cargo ?: "(Oferta no encontrada)",
                                        ubicacion = offer?.ubicacion ?: "No especificada",
                                        empleadorNombre = offer?.employerId?.let { employersMap[it] } ?: "Empleador desconocido",
                                        estado = p.estado_postulacion.ifBlank { "pendiente" },
                                        fechaPostulacion = p.fechaPostulacion
                                    )
                                }

                                adapter.submit(ui)
                                mostrarEstado(empty = ui.isEmpty())
                            }

                            override fun onCancelled(error: DatabaseError) {
                                if (!isAdded) return
                                adapter.submit(emptyList())
                                mostrarEstado(empty = true)
                            }
                        })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (!isAdded) return
                        adapter.submit(emptyList())
                        mostrarEstado(empty = true)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                adapter.submit(emptyList())
                mostrarEstado(empty = true)
            }
        }

        postulacionesQuery?.addValueEventListener(postulacionesListener ?: return)
    }

    private fun mostrarCargando(show: Boolean) {
        binding.progress.isVisible = show
    }

    private fun mostrarEstado(empty: Boolean) {
        mostrarCargando(false)
        binding.tvEmpty.isVisible = empty
        binding.rvPostulaciones.isVisible = !empty
    }

    override fun onDestroyView() {
        postulacionesQuery?.removeEventListener(postulacionesListener ?: return)
        _binding = null
        super.onDestroyView()
    }
}