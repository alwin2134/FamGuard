package com.famguard.agent.mirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/**
 * Trampoline Activity to request Android MediaProjection consent from the user.
 * Launched when the guardian sends a START_SCREEN_MIRROR command.
 */
class ScreenCapturePermissionActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection intent: ${e.message}", e)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Log.i(TAG, "MediaProjection permission granted by user. Starting ScreenMirroringService.")
                ScreenMirroringService.startService(this, resultCode, data)
            } else {
                Log.w(TAG, "MediaProjection permission denied or cancelled by user.")
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "ScreenCapturePerm"
        private const val REQUEST_CODE_CAPTURE = 9912

        fun start(context: Context) {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
