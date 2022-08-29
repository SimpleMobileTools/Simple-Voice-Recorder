package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.services.RecorderService

class BackgroundRecordActivity : SimpleActivity() {
    companion object {
        const val RECORD_INTENT_ACTION = "RECORD_ACTION"
    }

    override fun onResume() {
        super.onResume()
        if (intent.action == RECORD_INTENT_ACTION) {
            handlePermission(PERMISSION_POST_NOTIFICATIONS) {
                if (it) {
                    Intent(this@BackgroundRecordActivity, RecorderService::class.java).apply {
                        try {
                            if (RecorderService.isRunning) {
                                stopService(this)
                            } else {
                                startService(this)
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                } else {
                    toast(R.string.no_post_notifications_permissions)
                }
            }
        }
        moveTaskToBack(true)
        finish()
    }
}
