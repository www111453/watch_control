# WatchControl — 手表遥控手机

通过 Wear OS 手表远程控制 Android 手机的屏幕操作（滑动、点击），典型场景：用手表刷短视频。

## 项目结构
演示视频：【智能手表触摸控制器-控制手机刷视频-哔哩哔哩】 https://b23.tv/VS566Mg
```
WatchControl/
├── phone/    # 手机端 App（接收指令，执行手势）
├── watch/    # 手表端 App（发送指令的遥控器）
├── build.gradle.kts          # 根构建脚本 (AGP 8.7.0, Kotlin 1.9.24)
└── settings.gradle.kts
```

## 工作原理

```
[手表] —— 蓝牙 SPP ——> [手机]
  手势/摇晃              BluetoothSppServer 接收命令
                         ↓
                         WatchControlAccessibilityService
                         ↓
                         GestureExecutor.dispatchGesture()
                         （模拟屏幕滑动/点击）
```

1. 手机端启动蓝牙 SPP 服务器，等待手表连接
2. 手表端通过经典蓝牙 RFCOMM（UUID: `00001101-...`）连接手机，自动遍历所有配对设备尝试连接
3. 手表发送文本命令，手机解析后通过无障碍服务执行对应手势

## 手表端操作方式

手表端为全屏触摸 + 体感操作，无按钮：

| 操作 | 效果 | 对应命令 |
|------|------|---------|
| 上滑 | 下一个视频 | `CMD_SWIPE_UP` |
| 下滑 | 上一个视频 | `CMD_SWIPE_DOWN` |
| 单击 | 暂停/播放 | `CMD_TAP_CENTER` |
| 双击 | 点赞 | `CMD_DOUBLE_TAP` |
| 摇一摇 | 下一个视频 | `CMD_SWIPE_UP` |
| 长按 | 停止服务并退出 App | — |

响应：`ACK` 表示成功，`ERR:未知命令` 表示无法识别。

## 息屏运行

手表端使用前台服务（`WatchControlService`）保持后台运行：
- 蓝牙连接在息屏后不会断开
- 摇一摇检测在息屏后仍然有效（通过 WakeLock 保持 CPU 唤醒）
- 通知栏会显示"手表遥控运行中"常驻通知

## 核心文件

### phone 模块
- `MainActivity.kt` — 主界面，启动/停止蓝牙服务器，显示日志
- `BluetoothSppServer.kt` — 蓝牙 SPP 服务端，监听连接、收发命令
- `WatchControlAccessibilityService.kt` — 无障碍服务，提供 `dispatchGesture` 能力
- `GestureExecutor.kt` — 手势执行器，封装上滑/下滑/点击/双击操作

### watch 模块
- `MainActivity.kt` — 全屏触摸界面，手势识别（GestureDetector）
- `WatchControlService.kt` — 前台服务，管理蓝牙连接 + 加速度传感器（摇一摇）+ WakeLock
- `BluetoothSppClient.kt` — 蓝牙 SPP 客户端，遍历配对设备连接，含反射端口 fallback

## 使用步骤

1. 手机和手表先完成蓝牙配对
2. 在手机上安装 phone 模块，进入"设置 > 无障碍"开启"手表遥控手势执行服务"
3. 回到 App 点击"启动服务器"
4. 在手表上安装 watch 模块，App 启动后自动连接蓝牙并开始前台服务
5. 在手表上滑动/点击/摇晃即可远程控制手机屏幕
6. 长按手表屏幕可停止服务并退出

## 构建要求

- Android SDK: compileSdk 34
- 手机端 minSdk 24，手表端 minSdk 26
- Kotlin 1.9.24 / AGP 8.7.0
- 手表端权限：`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `WAKE_LOCK`, `FOREGROUND_SERVICE`
- 手机端权限：`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` + 无障碍服务
