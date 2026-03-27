package com.watchcontrol.phone

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BluetoothSppServer(
    private val onCommand: (String) -> String,
    private val onStatusChanged: (String) -> Unit
) {
    companion object {
        private const val TAG = "BtSppServer"
        private const val SERVICE_NAME = "WatchControl"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile private var running = false
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var acceptThread: Thread? = null
    private var readThread: Thread? = null

    fun start() {
        if (running) return
        running = true

        acceptThread = Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    onStatusChanged("蓝牙不可用")
                    return@Thread
                }
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                onStatusChanged("等待手表连接…")
                Log.i(TAG, "Waiting for connection...")

                while (running) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        clientSocket = socket
                        onStatusChanged("手表已连接: ${socket.remoteDevice.name ?: "未知设备"}")
                        Log.i(TAG, "Client connected: ${socket.remoteDevice.address}")
                        handleClient(socket)
                    } catch (e: Exception) {
                        if (running) {
                            Log.e(TAG, "Accept error", e)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth permission denied", e)
                onStatusChanged("蓝牙权限被拒绝")
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Server error", e)
                    onStatusChanged("服务器错误: ${e.message}")
                }
            }
        }.also { it.start() }
    }

    private fun handleClient(socket: BluetoothSocket) {
        readThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = PrintWriter(socket.outputStream, true)

                while (running && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "Received command: $line")
                    val response = onCommand(line.trim())
                    writer.println(response)
                    Log.d(TAG, "Sent response: $response")
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Client read error", e)
                }
            } finally {
                try { socket.close() } catch (_: Exception) {}
                clientSocket = null
                if (running) {
                    onStatusChanged("手表已断开，等待重连…")
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        onStatusChanged("服务器已停止")
        Log.i(TAG, "Server stopped")
    }
}
