package com.lkf.remotecontrol

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.annotation.SuppressLint
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lkf.remotecontrol.avi.ProjectionDecoder
import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.net.client.ClientStateListener
import com.lkf.remotecontrol.net.client.ProjectionClient
import com.lkf.remotecontrol.net.client.ProjectionClientManager
import com.lkf.remotecontrol.net.client.SimpleNetMessageReceiver
import com.lkf.remotecontrol.net.constants.CommandIds
import com.lkf.remotecontrol.net.models.NetMessage
import com.lkf.remotecontrol.net.models.OnlineDevice
import com.lkf.remotecontrol.net.models.ProjectionRequest
import com.lkf.remotecontrol.ui.RemoteControlTouchListener
import com.lkf.remotecontrol.utils.GsonHelper.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PullProjectionActivity : ComponentActivity(), SurfaceHolder.Callback, ClientStateListener, SimpleNetMessageReceiver {
    companion object {
        private const val TAG = "PullProjectionActivity"
        const val EXTRA_ONLINE_DEVICE = "onlineDevice"
    }

    private val client = ProjectionClientManager.client
    private val svProjection: SurfaceView by lazy { findViewById(R.id.svProjection) }
    private val btnLockScreen: Button by lazy { findViewById(R.id.btnLockScreen) }
    private val btnRecent: Button by lazy { findViewById(R.id.btnRecent) }
    private val btnHome: Button by lazy { findViewById(R.id.btnHome) }
    private val btnBack: Button by lazy { findViewById(R.id.btnBack) }
    private var decoder: ProjectionDecoder? = null
    private lateinit var onlineDevice: OnlineDevice

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!client.isOpen) {
            Toast.makeText(this, "服务端未连上", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val device = intent.getParcelableExtra(EXTRA_ONLINE_DEVICE) as OnlineDevice?
        if (device == null) {
            Toast.makeText(this, "无效设备", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        onlineDevice = device

        setContentView(R.layout.pull_projection_activity)

        svProjection.apply {
            setOnTouchListener(RemoteControlTouchListener(device.deviceId))
            post {
                val surfaceSize = calcFitSize(width, height)
                Log.d(TAG, "calcFitSize -> oldSize:[$width x $height] | newSize:[${surfaceSize.x} x ${surfaceSize.y}]")
                layoutParams.let { lp ->
                    lp.width = surfaceSize.x
                    lp.height = surfaceSize.y
                }
                requestLayout()
                holder.addCallback(this@PullProjectionActivity)
            }
        }

        val btnClick = object : OnClickListener {
            override fun onClick(v: View?) {
                if (v == null) return
                val action = when (v.id) {
                    R.id.btnBack -> GLOBAL_ACTION_BACK
                    R.id.btnHome -> GLOBAL_ACTION_HOME
                    R.id.btnRecent -> GLOBAL_ACTION_RECENTS
                    R.id.btnLockScreen -> GLOBAL_ACTION_LOCK_SCREEN
                    else -> return
                }
                val content = GSON.toJson(GlobalActionInput(device.deviceId, action))
                val msg = GSON.toJson(NetMessage(CommandIds.SendGlobalActionInput, content))
                ProjectionClientManager.client.send(msg)
            }
        }
        btnLockScreen.setOnClickListener(btnClick)
        btnRecent.setOnClickListener(btnClick)
        btnHome.setOnClickListener(btnClick)
        btnBack.setOnClickListener(btnClick)
    }

    private fun calcFitSize(surfaceW: Int, surfaceH: Int): Point {
        val projectionW = onlineDevice.width
        val projectionH = onlineDevice.height

        val adjSurfaceHeight = surfaceW * projectionH / projectionW
        return if (adjSurfaceHeight <= surfaceH) {
            Point(surfaceW, adjSurfaceHeight)
        } else {
            val adjSurfaceWidth = surfaceH * projectionW / projectionH
            Point(adjSurfaceWidth, surfaceH)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged -> w:$width | h:$height")
        startPullProjection(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        stopPullProjection()
    }

    override fun onProjectionStream(bytes: ByteArray) {
        decoder?.decodeData(bytes)
    }

    private var projectionStarted = false

    private fun startPullProjection(surface: Surface, w: Int, h: Int) {
        if (projectionStarted) return
        projectionStarted = true

        stopCurPullProjection()

        ProjectionClientManager.clientStateListeners.add(this)
        ProjectionClientManager.client.messageReceivers.add(this)
        decoder = ProjectionDecoder(surface, w, h).also { it.startDecode() }
        client.requestPullProjection(ProjectionRequest(onlineDevice.deviceId))
    }

    private fun stopPullProjection() {
        if (!projectionStarted) return
        projectionStarted = false
        stopCurPullProjection()
    }

    private fun stopCurPullProjection() {
        ProjectionClientManager.clientStateListeners.remove(this)
        ProjectionClientManager.client.messageReceivers.remove(this)
        decoder?.stopDecode()
        client.requestStopPullProjection(ProjectionRequest(onlineDevice.deviceId))
    }

    override fun onReceiveExitPullProjection() {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@PullProjectionActivity, "受控设备已离线", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onOpen(client: ProjectionClient) {
        Log.d(TAG, "ProjectionClient onOpen")
    }

    override fun onClose(client: ProjectionClient) {
        Log.w(TAG, "ProjectionClient onClose")
        handleNetworkDisconnected()
    }

    override fun onError(client: ProjectionClient, ex: Exception?) {
        Log.e(TAG, "ProjectionClient onError", ex)
        handleNetworkDisconnected()
    }

    private fun handleNetworkDisconnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@PullProjectionActivity, "网络异常", Toast.LENGTH_SHORT).show()
            if (!isFinishing) finish()
        }
    }
}