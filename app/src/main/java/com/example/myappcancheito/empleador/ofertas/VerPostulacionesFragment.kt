package com.example.myappcancheito.empleador.ofertas

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentVerPostulacionesBinding
import com.example.myappcancheito.postulante.aplicaciones.Postulacion
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
    private var queryRef: Query? = null

    data class Usuarios(
        var uid: String? = null,
        var nombre_completo: String? = null,
        var email: String? = null,
        var tipoUsuario: String? = null,
        var tiempo_registro: Any? = null,
        var ubicacion: String? = null,
        var formacion: String? = null,
        var experiencia: String? = null,
        var fotoPerfilUrl: String? = null,
        var cvUrl: String? = null,
        var usuario_verificado: Boolean? = null
    )

    companion object {
        private const val USERS_NODE = "Usuarios"
        private const val ARG_OFFER_ID = "offerId"

        fun newInstance(offerId: String) = VerPostulacionesFragment().apply {
            arguments = Bundle().apply { putString(ARG_OFFER_ID, offerId) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVerPostulacionesBinding.bind(view)

        offerId = arguments?.getString(ARG_OFFER_ID)
        if (offerId == null) {
            Toast.makeText(requireContext(), "Oferta no encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        cargarPostulaciones()
    }

    private fun cargarPostulaciones() {
        // Mantén la referencia para poder quitar el listener luego
        queryRef = db.child("postulaciones").orderByChild("offerId").equalTo(offerId)
        postulacionesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.llPostulaciones.removeAllViews()

                if (!snapshot.exists()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "No hay postulantes para esta oferta"
                        textSize = 16f
                        val p = dp(16)
                        setPadding(p, p, p, p)
                    }
                    binding.llPostulaciones.addView(tvEmpty)
                    return
                }

                val postulaciones = snapshot.children
                    .mapNotNull { it.getValue(Postulacion::class.java) }
                    .sortedByDescending { it.fechaPostulacion }

                postulaciones.forEach { p ->
                    val uid = p.postulanteId ?: return@forEach
                    cargarUsuarioYAgregarItem(uid, p.fechaPostulacion)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        queryRef!!.addValueEventListener(postulacionesListener as ValueEventListener)
    }

    private fun cargarUsuarioYAgregarItem(uid: String, fechaPostulacion: Long) {
        db.child(USERS_NODE).child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val user = snap.getValue(Usuarios::class.java)
                    binding.llPostulaciones.addView(construirItemUsuario(uid, user, fechaPostulacion))
                }
                override fun onCancelled(error: DatabaseError) {
                    binding.llPostulaciones.addView(construirItemUsuario(uid, null, fechaPostulacion))
                }
            })
    }

    private fun construirItemUsuario(uid: String, user: Usuarios?, fechaPostulacion: Long): View {
        val pad = dp(16)
        val cont = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = dp(8)
                setMargins(m, m, m, m)
            }
        }

        // Imagen de perfil
        val sizePx = dp(120)
        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                bottomMargin = dp(12)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        Glide.with(this@VerPostulacionesFragment)
            .load(user?.fotoPerfilUrl)
            .placeholder(R.drawable.person_24px)
            .error(R.drawable.person_24px)
            .into(imageView)

        cont.addView(imageView)

        // Nombre
        cont.addView(TextView(requireContext()).apply {
            text = "Nombre: ${user?.nombre_completo ?: "(sin nombre)"}"
            textSize = 16f
        })

        // Email
        cont.addView(TextView(requireContext()).apply {
            text = "Email: ${user?.email ?: "-"}"
            textSize = 14f
        })

        // Fecha postulación
        cont.addView(TextView(requireContext()).apply {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            text = "Fecha: ${dateFormat.format(Date(fechaPostulacion))}"
            textSize = 14f
        })

        // Click a detalle
        if (uid.isNotEmpty()) {
            cont.setOnClickListener { abrirDetallePostulante(uid) }
        }

        return cont
    }

    private fun abrirDetallePostulante(uid: String) {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.navFragment,
                DetallePostulanteFragment.newInstance(uid)
            )
            .addToBackStack(null)
            .commit()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        // Quita el listener del MISMO Query donde lo agregaste
        postulacionesListener?.let { listener ->
            queryRef?.removeEventListener(listener)
        }
        _binding = null
        super.onDestroyView()
    }
}
