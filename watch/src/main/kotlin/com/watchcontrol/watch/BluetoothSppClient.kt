package com.watchcontrol.watch

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BluetoothSppClient(
    private val onStatusChanged: (String) -> Unit,
    private val onResponse: (String) -> Unit
) {
    companion object {
        private const val TAG = "BtSppClient"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile private var connected = false
    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var readThread: Thread? = null

    fun connect() {
        if (connected) return

        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    onStatusChanged("蓝牙不可用")
                    return@Thread
                }

                val pairedDevices = adapter.bondedDevices
                if (pairedDevices.isNullOrEmpty()) {
                    onStatusChanged("无已配对设备")
                    return@Thread
                }

                adapter.cancelDiscovery()

                // 遍历所有配对设备，逐个尝试连接
                var connectedDevice: BluetoothDevice? = null
                for (device in pairedDevices) {
                    onStatusChanged("尝试连接: ${device.name ?: device.address}")
                    Log.i(TAG, "Trying ${device.name} (${device.address})")
                    val s = tryConnect(device)
                    if (s != null) {
                        socket = s
                        connectedDevice = device
                        break
                    }
                }

                if (socket == null || connectedDevice == null) {
                    onStatusChanged("所有设备连接失败\n长按重试")
                    return@Thread
                }

                connected = true
                writer = PrintWriter(socket!!.outputStream, true)
                onStatusChanged("已连接: ${connectedDevice.name}")
                Log.i(TAG, "Connected to ${connectedDevice.name}")

                // Start reading responses
                readThread = Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                        while (connected) {
                            val line = reader.readLine() ?: break
                            Log.d(TAG, "Received: $line")
                            onResponse(line)
                        }
                    } catch (e: Exception) {
                        if (connected) {
                            Log.e(TAG, "Read error", e)
                        }
                    } finally {
                        disconnect()
                    }
                }.also { it.start() }

            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth permission denied", e)
                onStatusChanged("蓝牙权限被拒绝")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                onStatusChanged("连接失败: ${e.message}")
                disconnect()
            }
        }.start()
    }

    fun sendCommand(command: String) {
        if (!connected) return
        Thread {
            try {
                writer?.println(command)
                Log.d(TAG, "Sent: $command")
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
                disconnect()
            }
        }.start()
    }

    fun disconnect() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
        onStatusChanged("未连接\n长按此处连接")
        Log.i(TAG, "Disconnected")
    }

    fun isConnected() = connected

    /**
     * 先用标准 SDP 方式连接，失败则用反射端口直连作为 fallback
     */
    private fun tryConnect(device: BluetoothDevice): BluetoothSocket? {
        // 方式1: 标准 SDP 查找
        try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            Log.i(TAG, "Connected via SDP to ${device.name}")
            return s
        } catch (e: Exception) {
            Log.w(TAG, "SDP connect failed for ${device.name}: ${e.message}")
        }

        // 方式2: 反射端口直连 (port 1)
        try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
            val s = method.invoke(device, 1) as BluetoothSocket
            s.connect()
            Log.i(TAG, "Connected via reflection to ${device.name}")
            return s
        } catch (e: Exception) {
            Log.w(TAG, "Reflection connect failed for ${device.name}: ${e.message}")
        }

        return null
    }
}
