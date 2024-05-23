package com.lkf.remotecontrol

import android.content.Context
import android.content.Intent

object Projections {
    fun startProjection(context: Context) {
        context.startActivity(Intent(context, StartProjectionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun stopProjection(context: Context) {
        context.startService(
            Intent(context, ProjectionService::class.java)
                .putExtra(ProjectionService.EXTRA_STOP_PROJECTION, true)
        )
    }
}