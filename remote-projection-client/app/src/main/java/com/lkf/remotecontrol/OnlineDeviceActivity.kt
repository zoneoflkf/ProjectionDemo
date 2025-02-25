package com.lkf.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.lkf.remotecontrol.net.client.ProjectionClientManager
import com.lkf.remotecontrol.net.client.SimpleNetMessageReceiver
import com.lkf.remotecontrol.net.models.OnlineDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Objects

class OnlineDeviceActivity : ComponentActivity(), SimpleNetMessageReceiver {
    private val rvDevices: RecyclerView by lazy { findViewById(R.id.rvDevices) }
    private var devicesAdapter: DevicesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ProjectionClientManager.client.isOpen) {
            Toast.makeText(this, "未连上服务器,请稍后", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.online_device_activity)

        rvDevices.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvDevices.adapter = DevicesAdapter(object : DiffUtil.ItemCallback<OnlineDevice>() {
            override fun areItemsTheSame(oldItem: OnlineDevice, newItem: OnlineDevice): Boolean {
                return oldItem.deviceId == newItem.deviceId
            }

            override fun areContentsTheSame(oldItem: OnlineDevice, newItem: OnlineDevice): Boolean {
                return Objects.equals(oldItem.deviceName, newItem.deviceName) &&
                        Objects.equals(oldItem.versionCode, newItem.versionCode) &&
                        Objects.equals(oldItem.versionName, newItem.versionName)
            }
        }).also { devicesAdapter = it }

        ProjectionClientManager.client.messageReceivers.add(this)

        //请求在线设备列表
        ProjectionClientManager.client.requestOnlineDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        ProjectionClientManager.client.messageReceivers.remove(this)
    }

    override fun onDeviceListMessage(devices: List<OnlineDevice>) {
        lifecycleScope.launch(Dispatchers.Main) {
            // 过滤出华为设备
            /*val devices = devices.filter {
                it.deviceName?.lowercase()?.contains("huawei") == true
            }*/
            devicesAdapter?.submitList(devices)
            if (devices.isEmpty()) {
                Toast.makeText(this@OnlineDeviceActivity, "无在线设备", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class DevicesAdapter(diffCallback: DiffUtil.ItemCallback<OnlineDevice>) : ListAdapter<OnlineDevice, DevicesViewHolder>(diffCallback) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
            return DevicesViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.online_device_item, parent, false))
        }

        override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
            val device = getItem(position)
            holder.tvDeviceInfo.apply {
                text = context.getString(
                    R.string.online_device_display_info,
                    device.deviceName, device.versionName, device.versionCode
                )
                setOnClickListener {
                    startActivity(
                        Intent(this@OnlineDeviceActivity, PullProjectionActivity::class.java)
                            .putExtra(PullProjectionActivity.EXTRA_ONLINE_DEVICE, device)
                    )
                    finish()
                }
            }
        }
    }

    private class DevicesViewHolder(itemView: View) : ViewHolder(itemView) {
        val tvDeviceInfo: Button

        init {
            tvDeviceInfo = itemView.findViewById(R.id.btnDeviceInfo)
        }
    }
}