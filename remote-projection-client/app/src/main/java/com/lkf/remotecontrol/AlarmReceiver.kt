package com.lkf.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.lkf.remotecontrol.constants.ProjectionAppConfigs
import com.lkf.remotecontrol.utils.DeviceUtil


class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val action = intent?.action ?: return

        Log.d(TAG, "onReceive > action: $action")

        if (Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM == action) {
            DeviceUtil.wakeupByAlarm(context)
        } else if (ProjectionAppConfigs.ACTION_KEEP_ALIVE == action) {
            context.apply {
                // 唤醒服务
                startService(
                    Intent(this, ProjectionService::class.java)
                        .putExtra(ProjectionService.EXTRA_KEEP_ALIVE, true)
                )
                // 重新设置下一个闹钟
                DeviceUtil.wakeupByAlarm(this)
            }
        }
    }
}