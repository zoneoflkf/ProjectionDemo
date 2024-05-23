package com.lkf.remotecontrol.ui

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.lkf.remotecontrol.models.BytesMessageProto
import com.lkf.remotecontrol.net.client.ProjectionClientManager

class RemoteControlTouchListener(private val deviceId: Long) : OnTouchListener {
    private val bytesMessageBuilder by lazy(BytesMessageProto.BytesMessage::newBuilder)
    private val touchInputsBuilder by lazy(BytesMessageProto.TouchInputs::newBuilder)
    private val motionEventBuilder by lazy(BytesMessageProto.MotionEvent::newBuilder)

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v == null || event == null) return false

        //使用JSON
        /*val content = GSON.toJson(TouchInput(device.deviceId, xAxis, yAxis, action))
        val netMsg = NetMessage(CommandIds.SendTouchInput, content)
        ProjectionClientManager.client.send(GSON.toJson(netMsg))*/

        //使用Protobuf
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchInputsBuilder.clear()
                    .setDeviceId(deviceId)
                    .appendMotionEvent(v, event)
            }

            MotionEvent.ACTION_MOVE -> touchInputsBuilder.appendMotionEvent(v, event)

            MotionEvent.ACTION_UP -> {
                touchInputsBuilder.appendMotionEvent(v, event)
                ProjectionClientManager.client.send(
                    bytesMessageBuilder.clear()
                        .setType(BytesMessageProto.Type.TOUCH_INPUT)
                        .setData(touchInputsBuilder.build().toByteString())
                        .build().toByteArray()
                )
            }
        }
        return true
    }

    private fun BytesMessageProto.TouchInputs.Builder.appendMotionEvent(v: View, event: MotionEvent): BytesMessageProto.TouchInputs.Builder {
        addMotionEvents(
            motionEventBuilder.clear()
                .setXAxis(event.x / v.width)
                .setYAxis(event.y / v.height)
                .setAction(event.actionMasked)
                .setTimestamp(System.currentTimeMillis())
                .build()
        )
        return this
    }
}