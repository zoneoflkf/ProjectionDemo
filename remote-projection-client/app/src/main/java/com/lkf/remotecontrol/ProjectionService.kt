package com.lkf.remotecontrol

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import com.google.protobuf.ByteString
import com.lkf.remotecontrol.avi.ProjectionDecoder
import com.lkf.remotecontrol.avi.ProjectionEncoder
import com.lkf.remotecontrol.models.BytesMessageProto
import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.client.ProjectionClientManager
import com.lkf.remotecontrol.net.client.SimpleNetMessageReceiver
import com.lkf.remotecontrol.net.models.ProjectionRequest
import com.lkf.remotecontrol.ui.DragFloatingTouchListener
import com.lkf.remotecontrol.utils.StringEx.toKvString


class ProjectionService : Service(), SimpleNetMessageReceiver {
    companion object {
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_STOP_PROJECTION = "stopProjection"
        const val EXTRA_KEEP_ALIVE = "keepAlive"

        private const val TAG: String = "ProjectionService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "ProjectionService"
        private const val SHOW_MY_PROJECTION = false
        private const val SHOW_MY_PROJECTION_FROM_REMOTE = false
    }

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var projectionEncoder: ProjectionEncoder? = null
    private val windowManager: WindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val encodedCallbacks = HashSet<(ByteArray) -> Unit>()

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        ProjectionClientManager.client.messageReceivers.add(this)
        //showFloating()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "投屏服务", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setContentTitle("投屏")
            .setContentText("投屏服务")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .build()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.getBooleanExtra(EXTRA_KEEP_ALIVE, false)) {
            Log.d(TAG, "onStartCommand: KEEP_ALIVE")
        } else {
            if (intent.getBooleanExtra(EXTRA_STOP_PROJECTION, false)) {
                Log.i(TAG, "onStartCommand: 停止投屏命令")
                stopProjection()
            } else {
                val resultCode = intent.getIntExtra("resultCode", Int.MIN_VALUE)
                val data = intent.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val width = intent.getIntExtra(EXTRA_WIDTH, 480)
                    val height = intent.getIntExtra(EXTRA_HEIGHT, 800)
                    startProjection(resultCode, data, width, height)
                } else {
                    Log.w(TAG, "onStartCommand: 无投屏参数 -> resultCode:$resultCode | data:${data?.extras.toKvString()}")
                }
            }
        }
        return START_STICKY
    }

    private var projectionWidth: Int = 0
    private var projectionHeight: Int = 0

    private fun startProjection(resultCode: Int, data: Intent, width: Int, height: Int) {
        if (projectionEncoder != null) {
            Log.w(TAG, "startProjection: 投屏已开启")
            return
        }
        val mp = projectionManager.getMediaProjection(resultCode, data)
        if (mp == null) {
            Log.e(TAG, "startProjection: 投屏开启失败")
            return
        }

        projectionWidth = width
        projectionHeight = height

        Log.i(TAG, "startProjection: -> size: [$width x $height]")

        ProjectionEncoder(mp, width, height) { encodedData ->
            //Log.d(TAG, "onEncoded -> sz:${encodedData.size} | data:${encodedData.contentToString()}")
            encodedCallbacks.forEach { cb ->
                cb.invoke(encodedData)
            }
        }.also {
            it.startEncoding()
            projectionEncoder = it
        }

        encodedCallbacks.add(pushStreamCallback)

        Log.i(TAG, "startProjection: 投屏开启成功 -> $width x $height")

        if (SHOW_MY_PROJECTION) showMyProjection(SHOW_MY_PROJECTION_FROM_REMOTE)
    }

    private fun stopProjection() {
        Log.i(TAG, "stopProjection: 停止投屏")

        encodedCallbacks.remove(pushStreamCallback)

        projectionEncoder?.stopEncoding()
        projectionEncoder = null

        if (SHOW_MY_PROJECTION) dismissMyProjection()
    }

    private val bytesMessageBuilder by lazy { BytesMessageProto.BytesMessage.newBuilder() }
    private val videoStreamBuilder by lazy { BytesMessageProto.VideoStream.newBuilder() }
    private val pushStreamCallback: (ByteArray) -> Unit = {
        val req = projectionRequest
        if (req != null) {
            val bytesMessage = bytesMessageBuilder.clear()
                .setType(BytesMessageProto.Type.VIDEO_STREAM)
                .setData(
                    videoStreamBuilder.clear()
                        .setDeviceId(req.deviceId)
                        .setData(ByteString.copyFrom(it))
                        .build().toByteString()
                ).build()
            ProjectionClientManager.client.send(bytesMessage.toByteArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection()
        ProjectionClientManager.client.messageReceivers.remove(this)
        //dismissFloating()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private var projectionRequest: ProjectionRequest? = null

    override fun onStartProjection(req: ProjectionRequest) {
        projectionRequest = req
        Projections.startProjection(this)
    }

    override fun onStopProjection() {
        projectionRequest = null
        Projections.stopProjection(this)
    }

    override fun onReceiveTouchInput(touchInput: TouchInput) {
        val service = ControllerAbService.instance
        if (service == null) {
            Log.w(TAG, "onReceiveTouchInput: 无障碍服务未初始化")
            return
        }
        service.sendTouchInput(touchInput)
    }

    override fun onReceiveGlobalAction(globalActionInput: GlobalActionInput) {
        val service = ControllerAbService.instance
        if (service == null) {
            Log.w(TAG, "onReceiveGlobalAction: 无障碍服务未初始化")
            return
        }
        service.performGlobalAction(globalActionInput.action)
    }

    private var keepAliveFloating: View? = null

    private fun showFloating() {
        runCatching {
            keepAliveFloating?.let { windowManager.removeView(it) }
            View.inflate(this, R.layout.keepalive_floating, null).also {
                keepAliveFloating = it
                it.setOnTouchListener(DragFloatingTouchListener())
                /*val w: Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
                val h: Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()*/
                val w = 1
                val h = 1
                windowManager.addView(it, WindowManager.LayoutParams().apply {
                    x = 0
                    y = 0
                    width = w
                    height = h
                    gravity = Gravity.START or Gravity.TOP
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                })
            }
        }
    }

    private fun dismissFloating() {
        runCatching {
            keepAliveFloating?.let { windowManager.removeView(it) }
        }
    }

    private var projectionView: SurfaceView? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun showMyProjection(fromRemote: Boolean) {
        projectionView = SurfaceView(applicationContext)
        projectionView?.holder?.addCallback(object : SurfaceHolder.Callback {
            var mySvDecoder: ProjectionDecoder? = null
            val encodedCallback: (ByteArray) -> Unit = {
                mySvDecoder?.decodeData(it)
            }
            val receiver: SimpleNetMessageReceiver = object : SimpleNetMessageReceiver {
                override fun onProjectionStream(bytes: ByteArray) {
                    mySvDecoder?.decodeData(bytes)
                }
            }

            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "testMyProjection surfaceCreated")
                if (fromRemote) {
                    //如果从远端解码,则解码服务器的推流数据
                    ProjectionClientManager.client.messageReceivers.add(receiver)
                } else {
                    //如果从本地端解码,则监听本地投屏端编码器的回调数据
                    encodedCallbacks.add(encodedCallback)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "testMyProjection surfaceChanged -> w:$width | h:$height")
                mySvDecoder?.stopDecode()
                mySvDecoder = ProjectionDecoder(holder.surface, width, height)
                mySvDecoder?.startDecode()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "testMyProjection surfaceDestroyed")
                mySvDecoder?.stopDecode()
                if (fromRemote) {
                    ProjectionClientManager.client.messageReceivers.remove(receiver)
                } else {
                    encodedCallbacks.remove(encodedCallback)
                }
            }
        })
        projectionView?.setOnTouchListener(DragFloatingTouchListener())
        windowManager.addView(projectionView, WindowManager.LayoutParams().apply {
            x = 0
            y = 0
            width = projectionWidth / 3
            height = projectionHeight / 3
            gravity = Gravity.START or Gravity.TOP
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        })
    }

    private fun dismissMyProjection() {
        projectionView?.let { windowManager.removeView(it) }
        projectionView = null
    }

    private val wakeLock by lazy {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return@lazy powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Projection:KeepAlive")
    }

    private fun acquireWakeLock() {
        wakeLock.acquire();
    }

    private fun releaseWakeLock() {
        wakeLock.release()
    }
}