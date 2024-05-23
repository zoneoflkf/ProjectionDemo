package com.lkf.remotecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.utils.DeviceUtil

class ControllerAbService : AccessibilityService() {
    companion object {
        private const val TAG = "ControllerAbService"
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
        private const val PROJECTION_PERMISSION_ACTIVITY = "com.android.systemui.media.MediaProjectionPermissionActivity"
        var instance: ControllerAbService? = null
    }

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        /*Log.i(
            TAG, "onAccessibilityEvent: type:${eventTypeToString(event.eventType)} " +
                    "| pkg:${event.packageName} " +
                    "| clz:${event.className} " +
                    "| viewRes:${event.source?.viewIdResourceName}"
        )*/
        if (SYSTEM_UI_PACKAGE_NAME == event.packageName && PROJECTION_PERMISSION_ACTIVITY == event.className) {
            //logViewHierarchy(rootInActiveWindow)
            findOkBtnAndClick(rootInActiveWindow)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    /*private fun logViewHierarchy(nodeInfo: AccessibilityNodeInfo?, depth: Int = 0) {
        if (nodeInfo == null) return
        var spacerString = ""
        for (i in 0 until depth) {
            spacerString += '-'
        }
        //Log the info you care about here... I choce classname and view resource name, because they are simple, but interesting.
        Log.d(TAG, spacerString + nodeInfo.className + " " + nodeInfo.viewIdResourceName)
        for (i in 0 until nodeInfo.childCount) {
            logViewHierarchy(nodeInfo.getChild(i), depth + 1)
        }
    }*/

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
                DeviceUtil.wakeUpAndUnlock() // 亮屏
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

    private val screenSize by lazy { DeviceUtil.getScreenSize() }
    private val touchPath: Path = Path()
    private var downTime: Long = 0

    fun sendTouchInput(touchInput: TouchInput) {
        val point = touchInput.toScreenPoint()
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