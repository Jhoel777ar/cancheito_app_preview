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
        private const val USERS_NODE = "Usuarios"

        fun newInstance(uid: String) = DetallePostulanteFragment().apply {
            arguments = Bundle().apply { putString(ARG_UID, uid) }
        }
    }

    private var _binding: FragmentDetallePostulanteBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var uid: String? = null
    private var currentCvUrl: String? = null

    // Permiso de escritura (solo necesario en API <= 28 si quieres guardar en /Download público)
    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && currentCvUrl != null) {
            iniciarDescargaCv(currentCvUrl!!)
        } else if (!granted) {
            Toast.makeText(requireContext(),"Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetallePostulanteBinding.bind(view)

        uid = arguments?.getString(ARG_UID)
        if (uid.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "UID inválido", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnDescargarCv.isEnabled = false
        binding.btnVerCv.isEnabled = false

        cargarUsuario(uid!!)
    }

    private fun cargarUsuario(uid: String) {
        db.child(USERS_NODE).child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val u = snapshot.getValue(VerPostulacionesFragment.Usuarios::class.java)
                    if (u == null) {
                        Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Foto
                    Glide.with(requireContext())
                        .load(u.fotoPerfilUrl)
                        .placeholder(R.drawable.person_24px)
                        .error(R.drawable.person_24px)
                        .into(binding.ivFoto)

                    // Datos
                    binding.tvNombre.text        = u.nombre_completo ?: "(sin nombre)"
                    binding.tvEmail.text         = u.email ?: "-"
                    binding.tvUbicacion.text     = u.ubicacion ?: "-"
                    binding.tvFormacion.text     = u.formacion ?: "-"
                    binding.tvExperiencia.text   = u.experiencia ?: "-"

                    currentCvUrl = u.cvUrl

                    if (!currentCvUrl.isNullOrBlank()) {
                        binding.btnDescargarCv.isEnabled = true
                        binding.btnVerCv.isEnabled = true

                        binding.btnDescargarCv.setOnClickListener {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                // API 28 o menor: podría requerir WRITE_EXTERNAL_STORAGE
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
                        Toast.makeText(requireContext(), "Este postulante no tiene CV cargado.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun abrirCvEnVisor(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // Sugerir MIME para que el chooser priorice visores PDF
            intent.setDataAndType(Uri.parse(url), "application/pdf")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se pudo abrir el CV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarDescargaCv(url: String) {
        try {
            val fileName = sugerirNombreArchivo(url.toUri(), binding.tvNombre.text?.toString(), uid)

            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Descargando CV…")
                .setMimeType("application/pdf")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            @Suppress("DEPRECATION")
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            dm.enqueue(request)
            Toast.makeText(requireContext(), "Descarga iniciada. Revisa notificaciones.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se pudo iniciar la descarga: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sugerirNombreArchivo(uri: Uri, nombre: CharSequence?, uid: String?): String {
        // Nombre base: "CV_<Nombre o UID>.pdf"
        val base = (nombre?.toString()?.ifBlank { null } ?: uid ?: "postulante")
            .replace("\\s+".toRegex(), "_")
            .replace("[^A-Za-z0-9_\\-]".toRegex(), "")
        // Si el path del enlace trae algo tipo ".../cv_1695760000000.pdf?alt=media&token=..."
        val last = uri.lastPathSegment ?: ""
        val posible = last.substringAfterLast('/').substringBefore('?')
        val ext = if (posible.endsWith(".pdf", ignoreCase = true)) ".pdf" else ".pdf"
        return "CV_${base}$ext"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

