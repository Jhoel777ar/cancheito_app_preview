package com.example.myappcancheito.empleador.ofertas

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myappcancheito.R
import com.example.myappcancheito.databinding.FragmentPublicarOfertaBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

class PublicarOfertaFragment : Fragment(R.layout.fragment_publicar_oferta) {

    private var _binding: FragmentPublicarOfertaBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var selectedDate: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPublicarOfertaBinding.bind(view)

        val opciones = listOf("Presencial", "Híbrido", "Remoto")
        binding.actvModalidad.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones)
        )

        binding.etFechaLimite.setOnClickListener { showDatePicker() }
        binding.btnPublicar.setOnClickListener { publicar() }
    }

    private fun showDatePicker() {
        val today = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona la fecha límite")
            .setSelection(today)
            .setCalendarConstraints(
                com.google.android.material.datepicker.CalendarConstraints.Builder()
                    .setStart(today)
                    .build()
            )
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selection
            }
            val localCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
                set(
                    utcCalendar.get(Calendar.YEAR),
                    utcCalendar.get(Calendar.MONTH),
                    utcCalendar.get(Calendar.DAY_OF_MONTH),
                    0, 0, 0
                )
                set(Calendar.MILLISECOND, 0)
            }
            selectedDate = localCalendar.timeInMillis
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getDefault()
            binding.etFechaLimite.setText(dateFormat.format(localCalendar.time))
        }
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun publicar() {
        val cargo = binding.etCargo.text?.toString()?.trim().orEmpty()
        val descripcion = binding.etDescripcion.text?.toString()?.trim().orEmpty()
        val modalidad = binding.actvModalidad.text?.toString()?.trim().orEmpty()
        val ubicacion = binding.etUbicacion.text?.toString()?.trim().orEmpty()
        val pago_aprox = binding.etPagoAprox.text?.toString()?.trim().orEmpty()
        val fecha_limite = selectedDate

        var ok = true
        if (cargo.isEmpty()) { binding.tilCargo.error = getString(R.string.error_requerido); ok = false } else binding.tilCargo.error = null
        if (descripcion.isEmpty()) { binding.tilDescripcion.error = getString(R.string.error_requerido); ok = false } else binding.tilDescripcion.error = null
        if (modalidad.isEmpty()) { binding.tilModalidad.error = getString(R.string.error_requerido); ok = false } else binding.tilModalidad.error = null
        if (ubicacion.isEmpty()) { binding.tilUbicacion.error = getString(R.string.error_requerido); ok = false } else binding.tilUbicacion.error = null
        if (pago_aprox.isEmpty()) { binding.tilPagoAprox.error = getString(R.string.error_requerido); ok = false } else binding.tilPagoAprox.error = null
        if (fecha_limite == null) { binding.tilFechaLimite.error = getString(R.string.error_requerido); ok = false } else binding.tilFechaLimite.error = null
        if (!ok) return

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), getString(R.string.toast_sesion), Toast.LENGTH_SHORT).show()
            return
        }

        val id = UUID.randomUUID().toString()
        val offer = Offer(
            id = id,
            employerId = uid,
            cargo = cargo,
            descripcion = descripcion,
            modalidad = modalidad,
            ubicacion = ubicacion,
            estado = "ACTIVA",
            pago_aprox = pago_aprox,
            fecha_limite = fecha_limite,
            createdAt = System.currentTimeMillis()
        )

        db.child("ofertas").child(id).setValue(offer)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), getString(R.string.toast_publicada), Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.navFragment, MisOfertasFragment())
                    .addToBackStack(null)
                    .commit()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), getString(R.string.toast_error, it.message), Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}