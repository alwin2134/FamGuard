package com.famguard.agent.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AppLockOverlayManager(
    private val context: Context,
    private val appLockManager: AppLockManager
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentOverlayView: View? = null
    private var currentLockedPackage: String? = null

    fun isShowing(packageName: String? = null): Boolean {
        if (currentOverlayView == null) return false
        return if (packageName != null) currentLockedPackage == packageName else true
    }

    fun showOverlay(packageName: String) {
        if (!Settings.canDrawOverlays(context)) {
            android.util.Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        if (isShowing(packageName)) {
            return
        }

        mainHandler.post {
            dismissOverlayInternal()

            val appLabel = getAppLabel(packageName)
            val overlayView = buildOverlayView(packageName, appLabel)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager?.addView(overlayView, layoutParams)
                currentOverlayView = overlayView
                currentLockedPackage = packageName
                android.util.Log.d(TAG, "Overlay shown successfully for $packageName")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error adding overlay view: ${e.message}", e)
            }
        }
    }

    fun dismissOverlay() {
        mainHandler.post {
            dismissOverlayInternal()
        }
    }

    private fun dismissOverlayInternal() {
        currentOverlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                android.util.Log.d(TAG, "Overlay dismissed for $currentLockedPackage")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        currentOverlayView = null
        currentLockedPackage = null
    }

    private fun buildOverlayView(packageName: String, appLabel: String): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F2090D16")) // 95% opacity dark slate
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(24)
            setPadding(pad, pad, pad, pad)

            val cardBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // slate-800
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#334155")) // slate-700
            }
            background = cardBg
        }

        val cardParams = FrameLayout.LayoutParams(
            dp(320),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            val margin = dp(24)
            setMargins(margin, margin, margin, margin)
        }

        // Lock Icon
        val iconView = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setColorFilter(Color.parseColor("#EF4444")) // Red-500
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                bottomMargin = dp(16)
            }
        }
        card.addView(iconView)

        // Title
        val titleView = TextView(context).apply {
            text = "App Locked by FamGuard"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        card.addView(titleView)

        // Subtitle with app label
        val subView = TextView(context).apply {
            text = "$appLabel is currently restricted by your Family Guardian."
            setTextColor(Color.parseColor("#94A3B8")) // slate-400
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
        }
        card.addView(subView)

        // Button: Return to Home Screen
        val homeButton = Button(context).apply {
            text = "Go to Home Screen"
            setTextColor(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Blue-600
                cornerRadius = dp(10).toFloat()
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                dismissOverlayInternal()
            }
        }
        card.addView(homeButton)

        // Button: EMERGENCY UNLOCK (BYPASS) for testing
        val bypassButton = Button(context).apply {
            text = "EMERGENCY UNLOCK (BYPASS)"
            setTextColor(Color.parseColor("#F59E0B")) // Amber-500
            typeface = Typeface.DEFAULT_BOLD
            val bypassBg = GradientDrawable().apply {
                setColor(Color.parseColor("#292524")) // Stone-800
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            background = bypassBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            setOnClickListener {
                appLockManager.bypassPackage(packageName)
                dismissOverlayInternal()
                Toast.makeText(
                    context,
                    "FamGuard: Emergency bypass activated for $appLabel",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG, "Emergency bypass tapped for $packageName")
            }
        }
        card.addView(bypassButton)

        root.addView(card, cardParams)
        return root
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val TAG = "AppLockOverlayManager"
    }
}
