package com.simplemobiletools.voicerecorder.services

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SplashActivity
import com.simplemobiletools.voicerecorder.helpers.GET_DURATION
import com.simplemobiletools.voicerecorder.helpers.RECORDER_RUNNING_NOTIF_ID
import com.simplemobiletools.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.IOException
import java.util.*

class RecorderService : Service() {
    private var currFilePath = ""
    private var duration = 0
    private var timer = Timer()
    private var recorder: MediaRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent.action) {
            GET_DURATION -> broadcastDuration()
            else -> startRecording()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        recorder?.release()
        recorder = null
    }

    // mp4 output format with aac encoding should produce good enough mp3 files according to https://stackoverflow.com/a/33054794/1967672
    private fun startRecording() {
        startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())
        duration = 0
        broadcastDuration()

        val baseFolder = if (isQPlus()) {
            cacheDir
        } else {
            val defaultFolder = File("$internalStoragePath/${getString(R.string.app_name)}")
            if (!defaultFolder.exists()) {
                defaultFolder.mkdir()
            }
            defaultFolder.absolutePath
        }

        currFilePath = "$baseFolder/${getCurrentFormattedDateTime()}.mp3"
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currFilePath)

            try {
                prepare()
                start()
                duration = 0
                timer = Timer()
                timer.scheduleAtFixedRate(getTimerTask(), 1000, 1000)
            } catch (e: IOException) {
                showErrorToast(e)
            }
        }
    }

    private fun stopRecording() {
        timer.cancel()

        recorder?.apply {
            stop()
            release()

            ensureBackgroundThread {
                if (isQPlus()) {
                    addFileInNewMediaStore()
                } else {
                    addFileInLegacyMediaStore()
                }
            }
        }
        recorder = null
    }

    @SuppressLint("InlinedApi")
    private fun addFileInNewMediaStore() {
        val audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val storeFilename = currFilePath.getFilenameFromPath()
        val newSongDetails = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, storeFilename)
            put(MediaStore.Audio.Media.TITLE, storeFilename)
            put(MediaStore.Audio.Media.MIME_TYPE, storeFilename.getMimeType())
        }

        val newUri = contentResolver.insert(audioCollection, newSongDetails)
        if (newUri == null) {
            toast(R.string.unknown_error_occurred)
            return
        }

        val outputStream = contentResolver.openOutputStream(newUri)
        val inputStream = getFileInputStreamSync(currFilePath)
        inputStream!!.copyTo(outputStream!!, DEFAULT_BUFFER_SIZE)
        recordingSavedSuccessfully(true)
    }

    private fun addFileInLegacyMediaStore() {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(currFilePath),
            arrayOf(currFilePath.getMimeType())
        ) { _, _ -> recordingSavedSuccessfully(false) }
    }

    private fun recordingSavedSuccessfully(showFilenameOnly: Boolean) {
        val title = if (showFilenameOnly) currFilePath.getFilenameFromPath() else currFilePath
        val msg = String.format(getString(R.string.recording_saved_successfully), title)
        toast(msg, Toast.LENGTH_LONG)
    }

    private fun getTimerTask() = object : TimerTask() {
        override fun run() {
            duration++
            broadcastDuration()
        }
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
        EventBus.getDefault().post(Events.RecordingDuration(duration))
    }
}
