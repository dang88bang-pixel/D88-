package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.d88.platform.R

/**
 * Agent-Chat: Aufgabe senden → Planung → Kette → Human Gate → Ergebnis.
 * Der Chat zeigt ausschließlich, was der Core entschieden hat.
 */
class ChatFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chatAdapter = ChatAdapter()
        val list = view.findViewById<RecyclerView>(R.id.chat_list)
        list.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        list.adapter = chatAdapter
        vm.chatLines.observe(viewLifecycleOwner) {
            chatAdapter.submit(it)
            list.scrollToPosition(it.size - 1)
        }

        val deviceSpinner = view.findViewById<Spinner>(R.id.device_spinner)
        vm.devices.observe(viewLifecycleOwner) { devices ->
            val labels = devices.map { "${it.deviceType} (${it.deviceId}) – ${it.role.name}" }
            deviceSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
        }
        vm.devices.observe(viewLifecycleOwner) { devices ->
            val input = view.findViewById<EditText>(R.id.input_intent)
            view.findViewById<MaterialButton>(R.id.btn_send).setOnClickListener {
                val index = deviceSpinner.selectedItemPosition
                if (index in devices.indices) {
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        vm.submitTask(devices[index].deviceId, text)
                        input.setText("")
                    }
                }
            }
        }
    }
}
