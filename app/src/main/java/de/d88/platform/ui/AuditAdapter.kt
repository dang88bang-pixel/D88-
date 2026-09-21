package de.d88.platform.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Causal-Audit-Liste (Observatory + Geräteansicht). */
class AuditAdapter : RecyclerView.Adapter<AuditAdapter.VH>() {

    private val items = mutableListOf<UiAudit>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    fun submit(list: List<UiAudit>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_audit, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.time.text = timeFormat.format(Date(e.timestamp))
        holder.type.text = e.type
        holder.device.text = e.deviceId ?: "–"
        holder.result.text = e.result ?: ""
        holder.hash.text = "hash: ${e.hash.take(16)}…"
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.audit_time)
        val type: TextView = view.findViewById(R.id.audit_type)
        val device: TextView = view.findViewById(R.id.audit_device)
        val result: TextView = view.findViewById(R.id.audit_result)
        val hash: TextView = view.findViewById(R.id.audit_hash)
    }
}
