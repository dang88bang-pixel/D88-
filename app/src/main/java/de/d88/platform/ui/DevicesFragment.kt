package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R

/** Liste aller D88-Geräte (Rolle, Trust, Sessions). */
class DevicesFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_devices, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = DevicesAdapter { device ->
            (activity as MainActivity).show(DeviceDetailFragment.newInstance(device.deviceId))
        }
        view.findViewById<RecyclerView>(R.id.devices_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        vm.devices.observe(viewLifecycleOwner) { adapter.submit(it) }
    }
}
