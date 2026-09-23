package com.localstream.localstream_mobile.server

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.localstream.localstream_mobile.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class DeviceFeedbackClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun send() {
        if (!BuildConfig.DEVICE_FEEDBACK_URL.startsWith("https://") || BuildConfig.DEVICE_FEEDBACK_TOKEN.isBlank()) {
            return
        }

        Thread {
            try {
                val connection = URL(BuildConfig.DEVICE_FEEDBACK_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.DEVICE_FEEDBACK_TOKEN}")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(buildPayload().toString().toByteArray(Charsets.UTF_8)) }
                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
                // Feedback delivery failure is isolated; server continues
            }
        }.start()
    }

    private fun buildPayload(): JSONObject {
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        val batteryIntent = appContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val battery = JSONObject()
            .put("percentage", (level * 100 / scale.coerceAtLeast(1)).coerceIn(0, 100))
            .put("is_charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            .put("charging_source", when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                else -> "NONE"
            })
        return JSONObject()
            .put("device_id", deviceId())
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("android_version", Build.VERSION.RELEASE)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("app_version", BuildConfig.VERSION_NAME))
            .put("battery", battery)
            .put("timestamp", java.time.Instant.now().toString())
    }

    private fun deviceId(): String {
        val existing = preferences.getString(DEVICE_ID, null)
        if (existing != null) return existing
        return "device-${UUID.randomUUID()}".also {
            preferences.edit().putString(DEVICE_ID, it).apply()
        }
    }

    private companion object {
        const val PREFERENCES = "device_feedback"
        const val DEVICE_ID = "device_id"
    }
}