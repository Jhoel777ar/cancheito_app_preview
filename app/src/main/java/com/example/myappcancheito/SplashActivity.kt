package com.example.myappcancheito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myappcancheito.empleador.EmpleadorActivity
import com.example.myappcancheito.postulante.MainActivityPostulante
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class SplashActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val saludoTextView = findViewById<TextView>(R.id.txtSaludo)
        saludoTextView.text = getGreeting()
        saludoTextView.alpha = 0f
        saludoTextView.translationY = 100f
        saludoTextView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1500)
            .start()

        firebaseAuth = FirebaseAuth.getInstance()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                checkConnectionAndStartCountdown()
            }
        } else {
            checkConnectionAndStartCountdown()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de notificaciones concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
            }
            checkConnectionAndStartCountdown()
        }
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "¡Buenos días! 😉"
            in 12..18 -> "¡Buenas tardes! 😎"
            else -> "¡Buenas noches! 🤓"
        }
    }

    private fun checkConnectionAndStartCountdown() {
        if (!isInternetAvailable(this)) {
            Toast.makeText(this, "Sin conexión a internet", Toast.LENGTH_LONG).show()
            startCountdown()
            return
        }
        val quality = getNetworkQuality(this)
        if (quality == "Muy lenta" || quality == "Lenta") {
            Toast.makeText(this, "Conexión $quality. Puede afectar el rendimiento.", Toast.LENGTH_LONG).show()
        }
        startCountdown()
    }

    private fun startCountdown() {
        object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                comprobarTipoUsuario()
            }
        }.start()
    }

    private fun comprobarTipoUsuario() {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser == null) {
            startActivity(Intent(this, SelecionarTipoActivity::class.java))
            finish()
        } else {
            val reference = FirebaseDatabase.getInstance().getReference("Usuarios")
            reference.child(firebaseUser.uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val userType = snapshot.child("tipoUsuario").value as? String
                        if (userType == "empleador") {
                            startActivity(Intent(this@SplashActivity, EmpleadorActivity::class.java))
                            finish()
                        } else if (userType == "postulante") {
                            startActivity(Intent(this@SplashActivity, MainActivityPostulante::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@SplashActivity, "Tipo de usuario desconocido", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@SplashActivity, "Error al leer datos: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun getNetworkQuality(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Sin conexión"
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return "Sin conexión"
        val downSpeed = activeNetwork.linkDownstreamBandwidthKbps
        return when {
            downSpeed < 500 -> "Muy lenta"
            downSpeed < 3000 -> "Lenta"
            downSpeed < 10000 -> "Buena"
            else -> "Muy buena"
        }
    }
}