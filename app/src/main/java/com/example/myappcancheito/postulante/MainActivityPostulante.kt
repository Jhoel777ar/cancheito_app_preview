package com.example.myappcancheito.postulante

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myappcancheito.R
import com.example.myappcancheito.SelecionarTipoActivity
import com.example.myappcancheito.databinding.ActivityMainPostulanteBinding
import com.example.myappcancheito.postulante.Nav_Fragments_Postulante.FragmentInicioP
import com.example.myappcancheito.postulante.Nav_Fragments_Postulante.FragmentPerfilP
import com.example.myappcancheito.postulante.Nav_Fragments_Postulante.MisPostulacionesFragment
import com.example.myappcancheito.postulante.Nav_Fragments_Postulante.PostulanteProfile
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivityPostulante : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainPostulanteBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var postulacionesListener: ChildEventListener? = null
    private var userListener: ValueEventListener? = null
    private var userQuery: Query? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainPostulanteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.appBarMain.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        replaceFragment(FragmentInicioP())
        binding.navigationView.setCheckedItem(R.id.op_inicio_c)
        binding.navigationView.setNavigationItemSelectedListener(this)

        firebaseAuth = FirebaseAuth.getInstance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        createChannelIfNeeded()

        comprobarSesion()
        cargarHeader()
        firebaseAuth.currentUser?.uid?.let { suscribirNotificacionesDePostulaciones(it) }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "postulaciones_channel",
                "Notificaciones de Postulaciones",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.navFragment, fragment).commit()
    }

    private fun comprobarSesion() {
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, SelecionarTipoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            Toast.makeText(this, "Registrate", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val ivFoto = headerView.findViewById<ImageView>(R.id.ivFoto)
        val tvNombre = headerView.findViewById<TextView>(R.id.tvNombre)
        val tvCorreo = headerView.findViewById<TextView>(R.id.tvCorreo)

        val user = firebaseAuth.currentUser ?: return
        tvNombre.text = user.displayName ?: "Nombre"
        tvCorreo.text = user.email ?: "correo@ejemplo.com"
        ivFoto.load(R.mipmap.ic_launcher_round) { transformations(CircleCropTransformation()) }

        userQuery = db.child("Usuarios").child(user.uid)
        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFinishing) {
                    val perfil = snapshot.getValue(PostulanteProfile::class.java)
                    tvNombre.text = perfil?.nombre_completo ?: user.displayName ?: "Nombre"
                    tvCorreo.text = perfil?.email ?: user.email ?: "correo@ejemplo.com"
                    perfil?.fotoPerfilUrl?.let {
                        ivFoto.load(it) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            placeholder(R.mipmap.ic_launcher_round)
                            error(R.mipmap.ic_launcher_round)
                        }
                    } ?: ivFoto.load(R.mipmap.ic_launcher_round) { transformations(CircleCropTransformation()) }

                    val estadoCuenta = snapshot.child("estadoCuenta").value?.toString()
                    if (estadoCuenta != "Activa") {
                        firebaseAuth.signOut()
                        val intent = Intent(this@MainActivityPostulante, SelecionarTipoActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        Toast.makeText(this@MainActivityPostulante, "Tu cuenta está suspendida", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFinishing) {
                    Toast.makeText(this@MainActivityPostulante, "Error al verificar datos", Toast.LENGTH_SHORT).show()
                }
            }
        }
        userQuery?.addValueEventListener(userListener as ValueEventListener)
    }

    private fun cerrarSesion() {
        firebaseAuth.signOut()
        val intent = Intent(this, SelecionarTipoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun notificarAceptacion(offerId: String) {
        db.child("ofertas").child(offerId).get().addOnSuccessListener { snapshot ->
            if (!isFinishing) {
                val offer = snapshot.getValue(Offer::class.java)
                val title = "¡Entrevista aceptada!"
                val body = "Se aceptó tu entrevista para el cargo ${offer?.cargo ?: "la oferta"}."

                val intent = Intent(this, MainActivityPostulante::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("offerId", offerId)
                }
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val notif = NotificationCompat.Builder(this, "postulaciones_channel")
                    .setSmallIcon(R.drawable.person_24px)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notif)
            }
        }.addOnFailureListener {
            if (!isFinishing) {
                Toast.makeText(this, "Error al cargar oferta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun suscribirNotificacionesDePostulaciones(uid: String) {
        postulacionesListener?.let {
            db.child("postulaciones").orderByChild("postulanteId").equalTo(uid).removeEventListener(it)
        }

        postulacionesListener = object : ChildEventListener {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onChildChanged(snap: DataSnapshot, previousChildName: String?) {
                val estado = snap.child("estado_postulacion").getValue(String::class.java)
                if (estado == "aceptado") {
                    val offerId = snap.child("offerId").getValue(String::class.java) ?: return
                    notificarAceptacion(offerId)
                }
            }
            override fun onChildAdded(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {
                if (!isFinishing) {
                    Toast.makeText(this@MainActivityPostulante, "Error en notificaciones", Toast.LENGTH_SHORT).show()
                }
            }
        }

        db.child("postulaciones").orderByChild("postulanteId").equalTo(uid)
            .addChildEventListener(postulacionesListener as ChildEventListener)
    }

    data class Offer(
        var id: String = "",
        var employerId: String = "",
        var cargo: String = "",
        var descripcion: String = "",
        var modalidad: String = "",
        var ubicacion: String = "",
        var estado: String = "ACTIVA",
        var pago_aprox: String = "",
        var fecha_limite: Long? = null,
        var createdAt: Long = System.currentTimeMillis()
    )

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.op_inicio_c -> replaceFragment(FragmentInicioP())
            R.id.op_mi_perfil_c -> replaceFragment(FragmentPerfilP())
            R.id.op_postulaciones_c -> replaceFragment(MisPostulacionesFragment())
            R.id.op_cerrar_sesion_c -> cerrarSesion()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onStart() {
        super.onStart()
        comprobarSesion()
        cargarHeader()
        firebaseAuth.currentUser?.uid?.let { suscribirNotificacionesDePostulaciones(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        userQuery?.removeEventListener(userListener ?: return)
        firebaseAuth.currentUser?.uid?.let { uid ->
            postulacionesListener?.let {
                db.child("postulaciones").orderByChild("postulanteId").equalTo(uid).removeEventListener(it)
            }
        }
    }
}