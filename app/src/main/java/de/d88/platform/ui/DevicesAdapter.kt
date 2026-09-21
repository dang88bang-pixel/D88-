package de.d88.platform.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R

/** Geräte-Liste (Dashboard + Geräte-Bereich). */
class DevicesAdapter(private val onClick: (UiDevice) -> Unit = {}) :
    RecyclerView.Adapter<DevicesAdapter.VH>() {

    private val items = mutableListOf<UiDevice>()

    fun submit(list: List<UiDevice>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        holder.title.text = d.headline
        holder.role.text = d.role.name
        holder.role.setTextColor(d.roleColor)
        holder.trust.text = d.trustLabel
        holder.trust.setTextColor(d.trustColor)
        holder.meta.text =
            "Plattform: ${d.platformVersion} · Sessions: ${d.activeSessions} · " +
                "Projekte: ${d.projectScopes.joinToString().ifBlank { "–" }}" +
                if (d.simulated) " · SIMULATION" else ""
        holder.itemView.setOnClickListener { onClick(d) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.device_title)
        val role: TextView = view.findViewById(R.id.device_role)
        val trust: TextView = view.findViewById(R.id.device_trust)
        val meta: TextView = view.findViewById(R.id.device_meta)
    }
}
