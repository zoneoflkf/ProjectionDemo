package com.lkf.remotecontrol

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.lkf.remotecontrol.avi.ProjectionConfigs.PROJECTION_SIZE_LIMIT
import com.lkf.remotecontrol.utils.StringEx.toKvString
import kotlin.math.max
import kotlin.math.min

class StartProjectionActivity : ComponentActivity() {
    companion object {
        private const val TAG: String = "StartProjectionActivity"
        private const val REQUEST_PERMISSION_FOREGROUND_SERVICE: Int = 101
        private const val REQUEST_MEDIA_PROJECTION: Int = 201
    }

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTurnScreenOn(true)
        setShowWhenLocked(true)

        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要开启前台服务权限", Toast.LENGTH_SHORT).show()
            requestPermissions(arrayOf(Manifest.permission.FOREGROUND_SERVICE), REQUEST_PERMISSION_FOREGROUND_SERVICE)
            finish()
            return
        }

        val intent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult -> reqCode:$requestCode | rltCode:$resultCode | data:${data?.extras.toKvString()}")
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                startProjectionService(resultCode, data)
            } else {
                Toast.makeText(this, "请求投屏失败 -> code:$resultCode", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun startProjectionService(resultCode: Int, data: Intent?) {
        startForegroundService(Intent(this, ProjectionService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
            val size = calcProjectionSize()
            putExtra(ProjectionService.EXTRA_WIDTH, size.x)
            putExtra(ProjectionService.EXTRA_HEIGHT, size.y)
        })
    }

    private fun calcProjectionSize(): Point {
        val screenSize = Point()
        windowManager.defaultDisplay.getRealSize(screenSize)
        val width: Int
        val height: Int
        if (screenSize.y > screenSize.x) {
            width = min(screenSize.x, PROJECTION_SIZE_LIMIT)
            height = width * screenSize.y / screenSize.x
        } else {
            height = min(screenSize.y, PROJECTION_SIZE_LIMIT)
            width = height * screenSize.x / screenSize.y
        }

        // 规定: 短边是宽, 长边是高
        val realWidth = min(width, height)
        val realHeight = max(width, height)

        return Point(realWidth, realHeight)
    }
}