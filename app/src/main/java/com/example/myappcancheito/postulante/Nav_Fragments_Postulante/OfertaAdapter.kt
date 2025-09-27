package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myappcancheito.databinding.ItemOfertaBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OfertaAdapter(
    private val ofertas: List<Offer>,
    private val onPostularClick: (Offer) -> Unit
) : RecyclerView.Adapter<OfertaAdapter.OfertaViewHolder>() {

    inner class OfertaViewHolder(val binding: ItemOfertaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfertaViewHolder {
        val binding = ItemOfertaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OfertaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OfertaViewHolder, position: Int) {
        val oferta = ofertas[position]
        holder.binding.apply {
            tvCargo.text = oferta.cargo
            tvDescripcion.text = oferta.descripcion
            tvUbicacion.text = oferta.ubicacion
            tvModalidad.text = oferta.modalidad
            tvPago.text = oferta.pago_aprox
            tvFechaLimite.text = oferta.fecha_limite?.let {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getDefault()
                dateFormat.format(it)
            } ?: "No especificada"

            btnPostular.setOnClickListener {
                onPostularClick(oferta)
            }
        }
    }

    override fun getItemCount(): Int = ofertas.size
}