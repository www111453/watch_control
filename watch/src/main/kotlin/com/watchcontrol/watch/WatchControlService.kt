package com.watchcontrol.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class WatchControlService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "WatchControlSvc"
        private const val CHANNEL_ID = "watch_control_channel"
        private const val NOTIFICATION_ID = 1
        private const val SHAKE_THRESHOLD = 15f
        private const val SHAKE_COOLDOWN_MS = 1000L
    }

    inner class LocalBinder : Binder() {
        val service: WatchControlService get() = this@WatchControlService
    }

    private val binder = LocalBinder()
    lateinit var client: BluetoothSppClient
        private set

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastShakeTime = 0L

    var onShake: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()

        client = BluetoothSppClient(
            onStatusChanged = { status ->
                Log.d(TAG, "Status: $status")
                onStatusChanged?.invoke(status)
            },
            onResponse = { /* Activity 会设置回调 */ }
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 保持 CPU 唤醒
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WatchControl::ShakeLock")
        wakeLock?.acquire()

        startForegroundNotification()
        Log.i(TAG, "Service created")
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "手表遥控", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持遥控服务运行" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("手表遥控运行中")
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val accel = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
        if (accel > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now
                client.sendCommand("CMD_SWIPE_UP")
                onShake?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        client.disconnect()
        Log.i(TAG, "Service destroyed")
    }
}