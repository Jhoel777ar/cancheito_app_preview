package com.example.myappcancheito.postulante.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myappcancheito.databinding.ItemOfertaBinding
import com.example.myappcancheito.empleador.ofertas.Offer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OffersUnifiedAdapter(
    private val onPostularClick: (Offer) -> Unit,
    private val onItemClick: (Offer) -> Unit = {}
) : ListAdapter<Offer, OffersUnifiedAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<Offer>() {
        override fun areItemsTheSame(o: Offer, n: Offer) = o.id == n.id
        override fun areContentsTheSame(o: Offer, n: Offer) = o == n
    }

    inner class VH(val b: ItemOfertaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(it: Offer) = with(b) {
            tvCargo.text = it.cargo
            tvDescripcion.text = it.descripcion
            tvUbicacion.text = it.ubicacion
            tvModalidad.text = it.modalidad
            tvPago.text = it.pago_aprox
            tvFechaLimite.text = it.fecha_limite?.let { ms ->
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }.format(ms)
            } ?: "No especificada"

            // Click en toda la tarjeta (opcional, por si quieres abrir detalle)
            root.setOnClickListener { _ -> onItemClick(it) }

            // Botón Postular
            btnPostular.setOnClickListener { _ -> onPostularClick(it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOfertaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
