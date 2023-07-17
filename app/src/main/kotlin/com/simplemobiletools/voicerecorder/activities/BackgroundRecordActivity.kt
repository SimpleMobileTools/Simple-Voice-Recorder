package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.openNotificationSettings
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.services.RecorderService

class BackgroundRecordActivity : SimpleActivity() {
    companion object {
        const val RECORD_INTENT_ACTION = "RECORD_ACTION"
    }

    override fun onResume() {
        super.onResume()
        if (intent.action == RECORD_INTENT_ACTION) {
            handleNotificationPermission { granted ->
                if (granted) {
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
                    PermissionRequiredDialog(this, R.string.allow_notifications_voice_recorder, { openNotificationSettings() })
                }
            }
        }
        moveTaskToBack(true)
        finish()
    }
}
