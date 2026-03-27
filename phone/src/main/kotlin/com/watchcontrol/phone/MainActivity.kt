package com.watchcontrol.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvConnection: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnToggleServer: Button
    private lateinit var btnAccessibility: Button

    private var server: BluetoothSppServer? = null
    private var serverRunning = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_BT_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvConnection = findViewById(R.id.tvConnection)
        tvLog = findViewById(R.id.tvLog)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        btnAccessibility = findViewById(R.id.btnAccessibility)

        btnToggleServer.setOnClickListener {
            if (serverRunning) stopServer() else startServer()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        requestBluetoothPermissions()
    }

    private fun startServer() {
        val service = WatchControlAccessibilityService.instance
        if (service == null) {
            appendLog("请先开启无障碍服务！")
            return
        }

        server = BluetoothSppServer(
            onCommand = { cmd -> handleCommand(cmd, service) },
            onStatusChanged = { status ->
                handler.post { tvConnection.text = status }
            }
        )
        server?.start()
        serverRunning = true
        tvStatus.text = getString(R.string.status_server_running)
        btnToggleServer.text = getString(R.string.btn_stop_server)
        appendLog("服务器已启动")
    }

    private fun stopServer() {
        server?.stop()
        server = null
        serverRunning = false
        tvStatus.text = getString(R.string.status_server_stopped)
        tvConnection.text = getString(R.string.status_client_disconnected)
        btnToggleServer.text = getString(R.string.btn_start_server)
        appendLog("服务器已停止")
    }

    private fun handleCommand(cmd: String, service: WatchControlAccessibilityService): String {
        appendLog("收到命令: $cmd")
        return when (cmd) {
            "CMD_SWIPE_UP" -> {
                GestureExecutor.swipeUp(service)
                appendLog("执行: 上滑")
                "ACK"
            }
            "CMD_SWIPE_DOWN" -> {
                GestureExecutor.swipeDown(service)
                appendLog("执行: 下滑")
                "ACK"
            }
            "CMD_TAP_CENTER" -> {
                GestureExecutor.tapCenter(service)
                appendLog("执行: 点击中心")
                "ACK"
            }
            "CMD_DOUBLE_TAP" -> {
                GestureExecutor.doubleTap(service)
                appendLog("执行: 双击")
                "ACK"
            }
            else -> {
                appendLog("未知命令: $cmd")
                "ERR:未知命令"
            }
        }
    }

    private fun appendLog(msg: String) {
        handler.post {
            tvLog.append("$msg\n")
        }
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
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}
