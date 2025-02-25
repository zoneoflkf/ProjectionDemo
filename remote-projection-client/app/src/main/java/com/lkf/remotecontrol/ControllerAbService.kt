package com.lkf.remotecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.utils.DeviceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ControllerAbService : AccessibilityService() {
    companion object {
        private const val TAG = "ControllerAbService"
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
        private const val PROJECTION_PERMISSION_ACTIVITY = "com.android.systemui.media.MediaProjectionPermissionActivity"
        var instance: ControllerAbService? = null
    }

    private val scope = GlobalScope

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    private var isInProjectionPromiseWindow = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        /*Log.i(
            TAG, "onAccessibilityEvent: type:${eventTypeToString(event.eventType)} " +
                    "| pkg:${event.packageName} " +
                    "| clz:${event.className} " +
                    "| viewRes:${event.source?.viewIdResourceName}"
        )*/

        val lastInProjectionPromiseWindow = isInProjectionPromiseWindow
        if (event.eventType == TYPE_WINDOW_STATE_CHANGED) {
            isInProjectionPromiseWindow = event.className?.contains("MediaProjectionPermissionActivity") == true
        }
        val justEnter = !lastInProjectionPromiseWindow && isInProjectionPromiseWindow
        if (justEnter) {
            allowProjectionSteps = 0
        }

        if (isInProjectionPromiseWindow) {
            //logViewHierarchy(rootInActiveWindow)

            val deviceName = DeviceUtil.getDeviceName()
            val apiVer = Build.VERSION.SDK_INT
            //Log.i(TAG, "onAccessibilityEvent: dev:$deviceName, sdk:$apiVer")

            if (deviceName.lowercase().contains("xiaomi")) {
                // 小米平板适配
                if (apiVer < 35) {
                    scope.launch(Dispatchers.Main) {
                        delay(1000)
                        performConfirmClick()
                    }
                } else {
                    scope.launch(Dispatchers.Main) {
                        delay(500)
                        traversalOnXiaoMi(rootInActiveWindow, 0)
                    }
                }
            } else {
                findOkBtnAndClick(rootInActiveWindow)
            }
        }
    }

    private var allowProjectionSteps = 0 // 操作同意投屏的步骤计数器

    private fun traversalOnXiaoMi(nodeInfo: AccessibilityNodeInfo?, depth: Int = 0) {
        if (nodeInfo == null) return

        if (nodeInfo.viewIdResourceName?.contains("screen_share_mode_spinner") == true) {
            if (allowProjectionSteps == 0) {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                allowProjectionSteps = 1
            }
        } else if (nodeInfo.text?.contains("整个屏幕") == true) {
            if (allowProjectionSteps == 1) {
                nodeInfo.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                allowProjectionSteps = 2
            }
        } else if (nodeInfo.viewIdResourceName?.contains("button1") == true || nodeInfo.text?.contains("开始") == true) {
            if (allowProjectionSteps == 2) {
                scope.launch(Dispatchers.Main) {
                    delay(500)

                    // 实测按钮模拟点击无效, 需要模拟手势
                    val okPoint = PointF(1109f, 2627f)
                    touchPath.reset()
                    touchPath.moveTo(okPoint.x, okPoint.y)
                    touchPath.lineTo(okPoint.x, okPoint.y)
                    realSendTouchInput(touchPath, 100)
                }
                allowProjectionSteps = 3
            }
        }

        if (allowProjectionSteps >= 3) return

        for (i in 0 until nodeInfo.childCount) {
            traversalOnXiaoMi(nodeInfo.getChild(i), depth + 1)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    private fun logViewHierarchy(nodeInfo: AccessibilityNodeInfo?, depth: Int = 0) {
        if (nodeInfo == null) return
        var spacerString = ""
        for (i in 0 until depth) {
            spacerString += '-'
        }
        //Log the info you care about here... I choce classname and view resource name, because they are simple, but interesting.
        Log.d(TAG, spacerString + nodeInfo.className + " " + nodeInfo.viewIdResourceName + " " + nodeInfo.text)
        for (i in 0 until nodeInfo.childCount) {
            logViewHierarchy(nodeInfo.getChild(i), depth + 1)
        }
    }

    //投屏授权弹窗自动点确定
    private fun findOkBtnAndClick(nodeInfo: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (nodeInfo == null) {
            return false
        }
        if (Button::class.java.name.equals(nodeInfo.className)) {
            val resIdName = nodeInfo.viewIdResourceName
            val text = nodeInfo.text
            Log.d(TAG, "findOkBtnAndClick: id=$resIdName, text=$text")
            if (resIdName.contains("button1") || "立即开始" == text) {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            if (findOkBtnAndClick(nodeInfo.getChild(i), depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun performConfirmClick() {
        // 小米平板适配: 模拟点击确认开始投屏
        // 1800 x 2880
        // PointF(1070.625, 1712.3755)
        val xAxis = 0.59479165f
        val yAxis = 0.5945748f

        val clickX = screenSize.x * xAxis
        val clickY = screenSize.y * yAxis

        touchPath.reset()
        touchPath.moveTo(clickX, clickY)
        touchPath.lineTo(clickX, clickY)

        realSendTouchInput(touchPath, 100)
    }

    private val screenSize by lazy { DeviceUtil.getScreenSize() }
    private val touchPath: Path = Path()
    private var downTime: Long = 0

    fun sendTouchInput(touchInput: TouchInput) {
        val point = touchInput.toScreenPoint()
        Log.d(TAG, "sendTouchInput > action:${touchInput.action}, pos:$point")
        when (touchInput.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = touchInput.timestamp
                touchPath.reset()
                touchPath.moveTo(point.x, point.y)
            }

            MotionEvent.ACTION_MOVE -> {
                touchPath.lineTo(point.x, point.y)
            }

            MotionEvent.ACTION_UP -> {
                touchPath.lineTo(point.x, point.y)
                Log.d(TAG, "sendTouchInput > duration: ${touchInput.timestamp - downTime}")
                realSendTouchInput(touchPath, touchInput.timestamp - downTime)
            }
        }
    }

    private fun TouchInput.toScreenPoint(): PointF {
        return PointF(xAxis * screenSize.x, yAxis * screenSize.y)
    }

    private fun realSendTouchInput(path: Path, duration: Long) {
        runCatching {
            val stroke = GestureDescription.StrokeDescription(path, 0, duration, false)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
        }.onFailure {
            Log.w(TAG, "realSendTouchInput: ex", it)
        }
    }
}