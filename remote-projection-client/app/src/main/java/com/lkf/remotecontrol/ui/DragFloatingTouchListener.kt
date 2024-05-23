package com.lkf.remotecontrol.ui

import android.annotation.SuppressLint
import android.app.Service
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class DragFloatingTouchListener : View.OnTouchListener {
    private var windowManager: WindowManager? = null
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v == null || event == null) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                (v.layoutParams as WindowManager.LayoutParams).apply {
                    x += (event.rawX - lastX).toInt()
                    y += (event.rawY - lastY).toInt()
                    updateViewLayout(v, this)
                }
                lastX = event.rawX
                lastY = event.rawY
            }
        }
        return true
    }

    private fun updateViewLayout(v: View, lp: WindowManager.LayoutParams) {
        var wm = windowManager
        if (wm == null) {
            wm = v.context.getSystemService(Service.WINDOW_SERVICE) as WindowManager
            windowManager = wm
        }
        wm.updateViewLayout(v, lp)
    }
}