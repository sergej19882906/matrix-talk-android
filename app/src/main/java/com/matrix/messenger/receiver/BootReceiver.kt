package com.matrix.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Received: ${intent.action}")
                ensureNotificationChannels(context)
                requestBatteryOptimizationBypass(context)
            }
        }
    }

    private fun ensureNotificationChannels(context: Context) {
        NotificationChannels.createAll(context)
    }

    private fun requestBatteryOptimizationBypass(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val packageName = context.packageName
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = "package:$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot request battery optimization bypass", e)
            }
        }
    }
}
