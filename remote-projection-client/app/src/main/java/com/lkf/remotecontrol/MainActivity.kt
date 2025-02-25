package com.lkf.remotecontrol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lkf.remotecontrol.constants.ProjectionAppConfigs.IS_CONTROLLED
import com.lkf.remotecontrol.net.client.ClientStateListener
import com.lkf.remotecontrol.net.client.ProjectionClient
import com.lkf.remotecontrol.net.client.ProjectionClientManager
import com.lkf.remotecontrol.utils.DeviceUtil
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), ClientStateListener {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
    }

    private val btnStartProjection: Button by lazy { findViewById(R.id.btnStartProjection) }
    private val btnStopProjection: Button by lazy { findViewById(R.id.btnStopProjection) }
    private val btnOnlineDevices: Button by lazy { findViewById(R.id.btnOnlineDevices) }
    private val btnAccessibility: Button by lazy { findViewById(R.id.btnAccessibility) }
    private val btnBatteryOpt: Button by lazy { findViewById(R.id.btnBatteryOpt) }


    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.main_activity)

        btnStartProjection.setOnClickListener { Projections.startProjection(this) }
        btnStopProjection.setOnClickListener { Projections.stopProjection(this) }
        btnOnlineDevices.setOnClickListener { startActivity(Intent(this, OnlineDeviceActivity::class.java)) }
        btnAccessibility.setOnClickListener { DeviceUtil.launchAccessibilitySetting(this) }
        btnBatteryOpt.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.setData(Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }

        if (IS_CONTROLLED) {
            btnOnlineDevices.visibility = View.GONE
            btnAccessibility.visibility = View.VISIBLE
            btnBatteryOpt.visibility = View.VISIBLE
        } else {
            btnOnlineDevices.visibility = View.VISIBLE
            btnAccessibility.visibility = View.GONE
            btnBatteryOpt.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        // 非受控端退出就杀进程
        if (!IS_CONTROLLED) {
            ProjectionClientManager.client.close()
            Process.killProcess(Process.myPid())
        }
    }

    override fun onResume() {
        super.onResume()

        ProjectionClientManager.clientStateListeners.add(this)

        if (IS_CONTROLLED) {
            btnAccessibility.apply {
                if (DeviceUtil.isAccessibilityEnabled()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = "无障碍(未开启)"
                }
            }

            btnBatteryOpt.apply {
                if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = "忽略电池优化(未开启)"

                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ProjectionClientManager.clientStateListeners.remove(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "忽略电池优化: 成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "忽略电池优化: 失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOpen(client: ProjectionClient) {
        lifecycleScope.launch { Toast.makeText(this@MainActivity, "服务器连接成功 ^_^", Toast.LENGTH_SHORT).show() }
    }

    override fun onClose(client: ProjectionClient) {
        lifecycleScope.launch { Toast.makeText(this@MainActivity, "服务器断开", Toast.LENGTH_SHORT).show() }
    }

    override fun onError(client: ProjectionClient, ex: Exception?) {
        lifecycleScope.launch { Toast.makeText(this@MainActivity, "服务器连接错误 -> ${ex?.message}", Toast.LENGTH_SHORT).show() }
    }

    override fun onBackPressed() {
        // 受控端直接退到后台，控制端才允许退出Activity
        if (IS_CONTROLLED) {
            moveTaskToBack(true)
        } else {
            finish()
        }
    }
}