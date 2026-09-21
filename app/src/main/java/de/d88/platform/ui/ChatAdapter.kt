package de.d88.platform.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Agent-Chat-Liste (USER / D88 / GATE). */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val items = mutableListOf<ChatLine>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)

    fun submit(list: List<ChatLine>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_line, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = items[position]
        holder.who.text = when (line.who) {
            ChatLine.Who.USER -> "DU"
            ChatLine.Who.D88 -> "D88"
            ChatLine.Who.GATE -> "HUMAN GATE"
        }
        holder.text.text = line.text
        holder.time.text = timeFormat.format(Date(line.time))
        holder.who.typeface =
            if (line.who == ChatLine.Who.GATE) Typeface.BOLD else Typeface.NORMAL
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val who: TextView = view.findViewById(R.id.chat_who)
        val text: TextView = view.findViewById(R.id.chat_text)
        val time: TextView = view.findViewById(R.id.chat_time)
    }
}
