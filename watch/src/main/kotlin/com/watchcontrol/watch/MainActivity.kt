package com.watchcontrol.watch

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var touchArea: View
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var service: WatchControlService? = null
    private var bound = false

    companion object {
        private const val REQUEST_BT_PERMISSIONS = 1001
        private const val SWIPE_THRESHOLD = 80
        private const val SWIPE_VELOCITY = 200
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as WatchControlService.LocalBinder).service
            service = svc
            bound = true

            svc.onStatusChanged = { status ->
                handler.post { tvStatus.text = status }
            }
            svc.onShake = {
                handler.post {
                    touchArea.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showAction("摇一摇 → 下一个")
                }
            }

            // 连接蓝牙
            requestBluetoothPermissions()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        touchArea = findViewById(R.id.touchArea)
        tvStatus = findViewById(R.id.tvStatus)
        tvHint = findViewById(R.id.tvHint)

        // 启动并绑定前台服务
        val intent = Intent(this, WatchControlService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                touchArea.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                service?.client?.sendCommand("CMD_TAP_CENTER")
                showAction("暂停/播放")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                touchArea.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                service?.client?.sendCommand("CMD_DOUBLE_TAP")
                showAction("点赞")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                touchArea.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                // 停止服务并退出
                stopService(Intent(this@MainActivity, WatchControlService::class.java))
                finishAffinity()
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                val dy = (e2.y) - (e1?.y ?: e2.y)
                if (Math.abs(dy) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY) {
                    touchArea.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (dy < 0) {
                        service?.client?.sendCommand("CMD_SWIPE_UP")
                        showAction("下一个")
                    } else {
                        service?.client?.sendCommand("CMD_SWIPE_DOWN")
                        showAction("上一个")
                    }
                    return true
                }
                return false
            }
        })

        touchArea.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showAction(text: String) {
        tvHint.text = text
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            tvHint.text = getString(R.string.hint_gesture)
        }, 1500)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_BT_PERMISSIONS)
                return
            }
        }
        service?.client?.connect()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                service?.client?.connect()
            } else {
                Toast.makeText(this, "需要蓝牙权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        // 注意：不停止 service，让它在后台继续运行
    }
}
