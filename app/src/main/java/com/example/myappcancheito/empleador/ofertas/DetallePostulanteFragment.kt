package com.example.myappcancheito.empleador.ofertas

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentDetallePostulanteBinding
import com.google.firebase.database.*

class DetallePostulanteFragment : Fragment(R.layout.fragment_detalle_postulante) {

    companion object {
        private const val ARG_UID = "uid"
        private const val ARG_OFFER_ID = "offerId"
        private const val USERS_NODE = "Usuarios"
        private const val POSTULACIONES_NODE = "postulaciones"

        fun newInstance(uid: String, offerId: String) = DetallePostulanteFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_UID, uid)
                putString(ARG_OFFER_ID, offerId)
            }
        }
    }

    private var _binding: FragmentDetallePostulanteBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var uid: String? = null
    private var offerId: String? = null
    private var currentCvUrl: String? = null

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && currentCvUrl != null) {
            iniciarDescargaCv(currentCvUrl!!)
        } else if (!granted) {
            Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetallePostulanteBinding.bind(view)

        uid = arguments?.getString(ARG_UID)
        offerId = arguments?.getString(ARG_OFFER_ID)

        if (uid.isNullOrEmpty() || offerId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Datos inválidos", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        binding.btnDescargarCv.isEnabled = false
        binding.btnVerCv.isEnabled = false

        binding.btnAceptarEntrevista.setOnClickListener {
            actualizarEstadoPostulacion("aceptado")
        }
        binding.btnRechazarEntrevista.setOnClickListener {
            actualizarEstadoPostulacion("rechazado")
        }

        cargarUsuario(uid!!)
    }

    private fun cargarUsuario(uid: String) {
        db.child(USERS_NODE).child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return // Evita NPE si la vista ya se destruyó
                val u = snapshot.getValue(VerPostulacionesFragment.Usuarios::class.java)
                if (u == null) {
                    Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return
                }

                Glide.with(requireContext())
                    .load(u.fotoPerfilUrl)
                    .placeholder(R.drawable.person_24px)
                    .error(R.drawable.person_24px)
                    .into(binding.ivFoto)

                binding.tvNombre.text = u.nombre_completo ?: "(sin nombre)"
                binding.tvEmail.text = u.email ?: "-"
                binding.tvUbicacion.text = u.ubicacion ?: "-"
                binding.tvFormacion.text = u.formacion ?: "-"
                binding.tvExperiencia.text = u.experiencia ?: "-"

                currentCvUrl = u.cvUrl
                if (!currentCvUrl.isNullOrBlank()) {
                    binding.btnDescargarCv.isEnabled = true
                    binding.btnVerCv.isEnabled = true
                    binding.btnDescargarCv.setOnClickListener {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            requestStoragePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            iniciarDescargaCv(currentCvUrl!!)
                        }
                    }
                    binding.btnVerCv.setOnClickListener {
                        abrirCvEnVisor(currentCvUrl!!)
                    }
                } else {
                    binding.btnDescargarCv.isEnabled = false
                    binding.btnVerCv.isEnabled = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        })
    }

    private fun actualizarEstadoPostulacion(nuevoEstado: String) {
        if (_binding == null) return
        val clave = "${offerId}_${uid}"
        setBotonesAccionEnabled(false)
        db.child(POSTULACIONES_NODE)
            .orderByChild("offerId_postulanteId")
            .equalTo(clave)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    if (!snapshot.hasChildren()) {
                        setBotonesAccionEnabled(true)
                        Toast.makeText(requireContext(), "Postulación no encontrada", Toast.LENGTH_SHORT).show()
                        return
                    }
                    snapshot.children.forEach { child ->
                        child.ref.child("estado_postulacion").setValue(nuevoEstado)
                            .addOnSuccessListener {
                                if (_binding == null) return@addOnSuccessListener
                                setBotonesAccionEnabled(true)
                                Toast.makeText(requireContext(), "Estado actualizado a $nuevoEstado", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                if (_binding == null) return@addOnFailureListener
                                setBotonesAccionEnabled(true)
                                Toast.makeText(requireContext(), "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding == null) return
                    setBotonesAccionEnabled(true)
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setBotonesAccionEnabled(enabled: Boolean) {
        if (_binding == null) return
        binding.btnAceptarEntrevista.isEnabled = enabled
        binding.btnRechazarEntrevista.isEnabled = enabled
    }

    private fun abrirCvEnVisor(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "application/pdf")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            if (_binding == null) return
            Toast.makeText(requireContext(), "No se pudo abrir el CV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarDescargaCv(url: String) {
        try {
            val fileName = sugerirNombreArchivo(url.toUri(), binding.tvNombre.text?.toString(), uid)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Descargando CV")
                setMimeType("application/pdf")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(requireContext(), "Descarga iniciada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            if (_binding == null) return
            Toast.makeText(requireContext(), "Error al iniciar descarga", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sugerirNombreArchivo(uri: Uri, nombre: CharSequence?, uid: String?): String {
        val base = (nombre?.toString()?.ifBlank { null } ?: uid ?: "postulante")
            .replace("\\s+".toRegex(), "_")
            .replace("[^A-Za-z0-9_\\-]".toRegex(), "")
        val last = uri.lastPathSegment ?: ""
        val ext = if (last.endsWith(".pdf", ignoreCase = true)) ".pdf" else ".pdf"
        return "CV_${base}$ext"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}