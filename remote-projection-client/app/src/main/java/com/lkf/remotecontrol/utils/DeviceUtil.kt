package com.lkf.remotecontrol.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Build.VERSION
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.lkf.remotecontrol.AlarmReceiver
import com.lkf.remotecontrol.ControllerAbService
import com.lkf.remotecontrol.ProjectionApp
import com.lkf.remotecontrol.constants.ProjectionAppConfigs.ACTION_KEEP_ALIVE
import com.lkf.remotecontrol.constants.ProjectionAppConfigs.WAKE_UP_INTERVAL_MS
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections


object DeviceUtil {
    private const val TAG = "DeviceUtil"

    fun getScreenSize(): Point {
        val screenSize = Point()
        val windowManager = ProjectionApp.instance.getSystemService(Service.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealSize(screenSize)
        return screenSize
    }

    fun getDeviceName(): String {
        return (Build.MANUFACTURER + " " + Build.MODEL).also {
            Log.i(TAG, "getDeviceName: $it")
        }
    }

    fun getVersionCode(): Int {
        return VERSION.SDK_INT.also {
            Log.i(TAG, "getVersionCode: $it")
        }
    }

    fun getVersionName(): String {
        return ("Android-" + VERSION.RELEASE).also {
            Log.i(TAG, "getVersionName: $it")
        }
    }

    /*fun getIpAddress(): String? {
        val context = ProjectionApp.instance
        val conMann = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mobileNetworkInfo = conMann.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
        val wifiNetworkInfo = conMann.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (mobileNetworkInfo!!.isConnected) {
            return getLocalIpAddress()
        } else if (wifiNetworkInfo!!.isConnected) {
            return getWifiAddress(context)
        }
        return ""
    }*/

    private fun getLocalIpAddress(): String? {
        try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in networkInterfaces) {
                val inetAddresses = Collections.list(ni.inetAddresses)
                for (address in inetAddresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.getHostAddress()
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return ""
    }

    private fun getWifiAddress(context: Context?): String {
        if (context == null) {
            return ""
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.connectionInfo
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        return intToIp(ipAddress)
    }

    private fun intToIp(ipInt: Int): String {
        return (ipInt and 0xFF).toString() + "." +
                (ipInt shr 8 and 0xFF) + "." +
                (ipInt shr 16 and 0xFF) + "." +
                (ipInt shr 24 and 0xFF)
    }

    fun isAccessibilityEnabled(): Boolean {
        val context = ProjectionApp.instance
        val localServiceStr: String = ComponentName(context.packageName, ControllerAbService::class.java.name).flattenToShortString()
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (info in list) {
            if (localServiceStr == info.id) {
                return true
            }
        }
        return false
    }

    fun launchAccessibilitySetting(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun wakeupByAlarm(context: Context) {
        val alarmManager = context.getSystemService(Application.ALARM_SERVICE) as AlarmManager

        if (VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "需要闹钟权限", Toast.LENGTH_SHORT).show()
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return
            }
        }

        // 设置闹钟 (允许低电量唤醒设备)
        val triggerAtMillis = System.currentTimeMillis() + WAKE_UP_INTERVAL_MS

        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_KEEP_ALIVE)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    /*fun wakeUpAndUnlock() {
        runCatching {
            // 获取电源管理器对象
            val context = ProjectionApp.instance
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                // 获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
                val wl = pm.newWakeLock(
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK, ":bright"
                )
                wl.acquire(10000) // 点亮屏幕
                wl.release() // 释放
            }
            // 屏幕解锁
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val keyguardLock = km.newKeyguardLock("unLock")
            // 屏幕锁定
            keyguardLock.reenableKeyguard()
            keyguardLock.disableKeyguard() // 解锁
        }.onFailure {
            Log.w(TAG, "wakeUpAndUnlock: failed", it)
        }
    }*/
}