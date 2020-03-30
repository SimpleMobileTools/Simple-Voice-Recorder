package com.simplemobiletools.voicerecorder.services

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.getLaunchIntent
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SplashActivity
import com.simplemobiletools.voicerecorder.helpers.GET_DURATION
import com.simplemobiletools.voicerecorder.helpers.RECORDER_RUNNING_NOTIF_ID
import com.simplemobiletools.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus

class RecorderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent.action
        if (action == GET_DURATION) {
            broadcastDuration()
        } else {
            startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())
        }

        return START_NOT_STICKY
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showNotification(): Notification {
        val channelId = "simple_recorder"
        val label = getString(R.string.app_name)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isOreoPlus()) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            NotificationChannel(channelId, label, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        val builder = NotificationCompat.Builder(this)
            .setContentTitle(label)
            .setContentText(getString(R.string.recording))
            .setSmallIcon(R.drawable.ic_mic_vector)
            .setContentIntent(getOpenAppIntent())
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)
            .setChannelId(channelId)

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
        return PendingIntent.getActivity(this, RECORDER_RUNNING_NOTIF_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcastDuration() {
        EventBus.getDefault().post(Events.RecordingDuration(3))
    }
}
