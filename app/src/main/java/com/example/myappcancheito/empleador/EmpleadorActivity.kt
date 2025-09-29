package com.example.myappcancheito.empleador

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myappcancheito.R
import com.example.myappcancheito.SelecionarTipoActivity
import com.example.myappcancheito.databinding.ActivityEmpleadorBinding
import com.example.myappcancheito.empleador.Nav_fragment_Empleador.EmpleadorProfile
import com.example.myappcancheito.empleador.Nav_fragment_Empleador.FragmentInicioV
import com.example.myappcancheito.empleador.Nav_fragment_Empleador.FragmentPerfilEm
import com.example.myappcancheito.empleador.Nav_fragment_Empleador.MisPostulacionesFragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class EmpleadorActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var binding: ActivityEmpleadorBinding
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEmpleadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.appBarMain.toolbar
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        replaceFragment(FragmentInicioV())
        binding.navigationView.setCheckedItem(R.id.op_inicio_v)

        binding.navigationView.setNavigationItemSelectedListener(this)

        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        comprobarSesion()
        cargarHeader()
        checkAccountStatus()
    }

    private fun checkAccountStatus() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userRef = database.getReference("Usuarios").child(uid)
        userRef.child("estadoCuenta").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val estadoCuenta = snapshot.value?.toString()
                if (estadoCuenta != "Activa") {
                    firebaseAuth.signOut()
                    val intent = Intent(this@EmpleadorActivity, SelecionarTipoActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    Toast.makeText(this@EmpleadorActivity, "Tu cuenta está suspendida", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EmpleadorActivity, "Error al verificar estado: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navFragment, fragment)
            .commit()
    }

    private fun cargarHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val ivFoto = headerView.findViewById<ImageView>(R.id.ivFoto)
        val tvNombre = headerView.findViewById<TextView>(R.id.tvNombre)
        val tvCorreo = headerView.findViewById<TextView>(R.id.tvCorreo)

        val user = firebaseAuth.currentUser ?: return
        tvNombre.text = user.displayName ?: "Nombre"
        tvCorreo.text = user.email ?: "correo@ejemplo.com"
        ivFoto.load(R.mipmap.ic_launcher_round)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ref = FirebaseDatabase.getInstance().getReference("Usuarios").child(user.uid)
                val snapshot = withContext(Dispatchers.IO) { ref.get().await() }
                val perfil = snapshot.getValue(EmpleadorProfile::class.java)
                tvNombre.text = perfil?.nombre_completo ?: user.displayName ?: "Nombre"
                tvCorreo.text = perfil?.email ?: user.email ?: "correo@ejemplo.com"
                perfil?.fotoPerfilUrl?.let {
                    ivFoto.load(it) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.mipmap.ic_launcher_round)
                        error(R.mipmap.ic_launcher_round)
                    }
                } ?: ivFoto.load(R.mipmap.ic_launcher_round) {
                    transformations(CircleCropTransformation())
                }
            } catch (e: Exception) {
                ivFoto.load(R.mipmap.ic_launcher_round) {
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        comprobarSesion()
        cargarHeader()
    }

    private fun cerrarSesion() {
        firebaseAuth.signOut()
        val intent = Intent(this, SelecionarTipoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }

    private fun comprobarSesion() {
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, SelecionarTipoActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            Toast.makeText(this, "Registrate", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.op_inicio_v -> replaceFragment(FragmentInicioV())
            R.id.op_postulaciones_v -> replaceFragment(MisPostulacionesFragment())
            R.id.op_publicar_oferta -> replaceFragment(
                com.example.myappcancheito.empleador.ofertas.PublicarOfertaFragment()
            )
            R.id.op_mi_perfil_v -> replaceFragment(FragmentPerfilEm())
            R.id.op_mis_ofertas -> replaceFragment(
                com.example.myappcancheito.empleador.ofertas.MisOfertasFragment()
            )
            R.id.ap_cerrar_sesion -> cerrarSesion()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}