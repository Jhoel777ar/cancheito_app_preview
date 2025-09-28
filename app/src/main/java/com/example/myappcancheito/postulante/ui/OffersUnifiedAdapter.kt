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

    // --- NUEVO: set de ofertas ya aplicadas ---
    private var appliedOffers: Set<String> = emptySet()
    fun updateAppliedOffers(newSet: Set<String>) {
        appliedOffers = newSet
        notifyDataSetChanged() // refresca estado del botón
    }

    object DIFF : DiffUtil.ItemCallback<Offer>() {
        override fun areItemsTheSame(o: Offer, n: Offer) = o.id == n.id
        override fun areContentsTheSame(o: Offer, n: Offer) = o == n
    }

    inner class VH(private val b: ItemOfertaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(of: Offer) = with(b) {
            tvCargo.text = of.cargo
            tvDescripcion.text = of.descripcion
            tvUbicacion.text = of.ubicacion
            tvModalidad.text = of.modalidad
            tvPago.text = of.pago_aprox
            tvFechaLimite.text = of.fecha_limite?.let { ms ->
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }.format(ms)
            } ?: "No especificada"

            root.setOnClickListener { onItemClick(of) }

            // --- NUEVO: estado del botón según appliedOffers ---
            val yaPostulado = appliedOffers.contains(of.id)
            btnPostular.isEnabled = !yaPostulado
            btnPostular.text = if (yaPostulado) "Ya postulaste" else "Postular"
            btnPostular.setOnClickListener {
                if (!yaPostulado) onPostularClick(of)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOfertaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}
