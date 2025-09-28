package com.example.myappcancheito.postulante.Nav_Fragments_Postulante

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myappcancheito.databinding.ItemMiPostulacionBinding
import java.text.SimpleDateFormat
import java.util.*

data class PostulacionUI(
    val cargo: String,
    val estado: String,
    val fechaPostulacion: Long? = null
)

class MisPostulacionesAdapter(
    private val items: MutableList<PostulacionUI> = mutableListOf()
) : RecyclerView.Adapter<MisPostulacionesAdapter.VH>() {

    fun submit(list: List<PostulacionUI>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemMiPostulacionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        val binding = ItemMiPostulacionBinding.inflate(inf, parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.b.tvCargo.text = it.cargo
        holder.b.tvEstado.text = "Estado: ${it.estado}"

        val f = it.fechaPostulacion
        holder.b.tvFecha.text = if (f != null && f > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            "Postulado: ${sdf.format(Date(f))}"
        } else ""
    }
}
