package com.watchcontrol.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log

object GestureExecutor {
    private const val TAG = "GestureExecutor"
    private const val SWIPE_DURATION_MS = 300L
    private const val TAP_DURATION_MS = 50L
    private const val DOUBLE_TAP_INTERVAL_MS = 100L

    private fun getScreenSize(service: AccessibilityService): Point {
        val wm = service.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getSize(point)
        return point
    }

    fun swipeUp(service: AccessibilityService, callback: ((Boolean) -> Unit)? = null) {
        val size = getScreenSize(service)
        val centerX = size.x / 2f
        val startY = size.y * 0.75f
        val endY = size.y * 0.25f
        performSwipe(service, centerX, startY, centerX, endY, callback)
    }

    fun swipeDown(service: AccessibilityService, callback: ((Boolean) -> Unit)? = null) {
        val size = getScreenSize(service)
        val centerX = size.x / 2f
        val startY = size.y * 0.25f
        val endY = size.y * 0.75f
        performSwipe(service, centerX, startY, centerX, endY, callback)
    }

    fun tapCenter(service: AccessibilityService, callback: ((Boolean) -> Unit)? = null) {
        val size = getScreenSize(service)
        val cx = size.x / 2f
        val cy = size.y / 2f
        performTap(service, cx, cy, callback)
    }

    fun doubleTap(service: AccessibilityService, callback: ((Boolean) -> Unit)? = null) {
        val size = getScreenSize(service)
        val cx = size.x / 2f
        val cy = size.y / 2f
        performTap(service, cx, cy) { firstOk ->
            if (firstOk) {
                Handler(Looper.getMainLooper()).postDelayed({
                    performTap(service, cx, cy, callback)
                }, DOUBLE_TAP_INTERVAL_MS)
            } else {
                callback?.invoke(false)
            }
        }
    }

    private fun performSwipe(
        service: AccessibilityService,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        callback: ((Boolean) -> Unit)?
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    private fun performTap(
        service: AccessibilityService,
        x: Float, y: Float,
        callback: ((Boolean) -> Unit)?
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed at ($x, $y)")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled")
                callback?.invoke(false)
            }
        }, null)
    }
}
