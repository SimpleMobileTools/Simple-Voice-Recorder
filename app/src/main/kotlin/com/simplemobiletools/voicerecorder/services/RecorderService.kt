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
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SplashActivity
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.extensions.updateWidgets
import com.simplemobiletools.voicerecorder.helpers.*
import com.simplemobiletools.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.*

class RecorderService : Service() {
    companion object {
        var isRunning = false
    }

    private val AMPLITUDE_UPDATE_MS = 75L

    private var currFilePath = ""
    private var duration = 0
    private var status = RECORDING_STOPPED
    private var durationTimer = Timer()
    private var amplitudeTimer = Timer()
    private var recorder: MediaRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent.action) {
            GET_RECORDER_INFO -> broadcastRecorderInfo()
            STOP_AMPLITUDE_UPDATE -> amplitudeTimer.cancel()
            TOGGLE_PAUSE -> togglePause()
            else -> startRecording()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        isRunning = false
        updateWidgets(false)
    }

    // mp4 output format with aac encoding should produce good enough m4a files according to https://stackoverflow.com/a/33054794/1967672
    private fun startRecording() {
        isRunning = true
        updateWidgets(true)
        if (status == RECORDING_RUNNING) {
            return
        }

        val baseFolder = if (isQPlus()) {
            cacheDir
        } else {
            val defaultFolder = File(config.saveRecordingsFolder)
            if (!defaultFolder.exists()) {
                defaultFolder.mkdir()
            }

            defaultFolder.absolutePath
        }

        currFilePath = "$baseFolder/${getCurrentFormattedDateTime()}.${config.getExtensionText()}"

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setOutputFormat(config.getOutputFormat())
                setAudioEncoder(config.getAudioEncoder())
                setAudioEncodingBitRate(config.bitrate)
                setAudioSamplingRate(44100)

                if (!isQPlus() && isPathOnSD(currFilePath)) {
                    var document = getDocumentFile(currFilePath.getParentPath())
                    document = document?.createFile("", currFilePath.getFilenameFromPath())

                    val outputFileDescriptor = contentResolver.openFileDescriptor(document!!.uri, "w")!!.fileDescriptor
                    setOutputFile(outputFileDescriptor)
                } else {
                    setOutputFile(currFilePath)
                }

                prepare()
                start()
                duration = 0
                status = RECORDING_RUNNING
                broadcastRecorderInfo()
                startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())

                durationTimer = Timer()
                durationTimer.scheduleAtFixedRate(getDurationUpdateTask(), 1000, 1000)

                startAmplitudeUpdates()
            }
        } catch (e: Exception) {
            showErrorToast(e)
            stopRecording()
        }
    }

    private fun stopRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        status = RECORDING_STOPPED

        recorder?.apply {
            try {
                stop()
                release()

                ensureBackgroundThread {
                    if (isQPlus()) {
                        addFileInNewMediaStore()
                    } else {
                        addFileInLegacyMediaStore()
                    }
                    EventBus.getDefault().post(Events.RecordingCompleted())
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
        recorder = null
    }

    private fun broadcastRecorderInfo() {
        broadcastDuration()
        broadcastStatus()
        startAmplitudeUpdates()
    }

    private fun startAmplitudeUpdates() {
        amplitudeTimer.cancel()
        amplitudeTimer = Timer()
        amplitudeTimer.scheduleAtFixedRate(getAmplitudeUpdateTask(), 0, AMPLITUDE_UPDATE_MS)
    }

    @SuppressLint("NewApi")
    private fun togglePause() {
        try {
            if (status == RECORDING_RUNNING) {
                recorder?.pause()
                status = RECORDING_PAUSED
            } else if (status == RECORDING_PAUSED) {
                recorder?.resume()
                status = RECORDING_RUNNING
            }
            broadcastStatus()
            startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    @SuppressLint("InlinedApi")
    private fun addFileInNewMediaStore() {
        val audioCollection = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val storeFilename = currFilePath.getFilenameFromPath()

        val newSongDetails = ContentValues().apply {
            put(Media.DISPLAY_NAME, storeFilename)
            put(Media.TITLE, storeFilename)
            put(Media.MIME_TYPE, storeFilename.getMimeType())
            put(Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Recordings")
        }

        val newUri = contentResolver.insert(audioCollection, newSongDetails)
        if (newUri == null) {
            toast(R.string.unknown_error_occurred)
            return
        }

        try {
            val outputStream = contentResolver.openOutputStream(newUri)
            val inputStream = getFileInputStreamSync(currFilePath)
            inputStream!!.copyTo(outputStream!!, DEFAULT_BUFFER_SIZE)
            recordingSavedSuccessfully()
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun addFileInLegacyMediaStore() {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(currFilePath),
            arrayOf(currFilePath.getMimeType())
        ) { _, _ -> recordingSavedSuccessfully() }
    }

    private fun recordingSavedSuccessfully() {
        toast(R.string.recording_saved_successfully)
    }

    private fun getDurationUpdateTask() = object : TimerTask() {
        override fun run() {
            if (status == RECORDING_RUNNING) {
                duration++
                broadcastDuration()
            }
        }
    }

    private fun getAmplitudeUpdateTask() = object : TimerTask() {
        override fun run() {
            if (recorder != null) {
                try {
                    EventBus.getDefault().post(Events.RecordingAmplitude(recorder!!.maxAmplitude))
                } catch (ignored: Exception) {
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showNotification(): Notification {
        val hideNotification = config.hideNotification
        val channelId = "simple_recorder"
        val label = getString(R.string.app_name)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isOreoPlus()) {
            val importance = if (hideNotification) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT
            NotificationChannel(channelId, label, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        var priority = Notification.PRIORITY_DEFAULT
        var icon = R.drawable.ic_microphone_vector
        var title = label
        var visibility = NotificationCompat.VISIBILITY_PUBLIC
        var text = getString(R.string.recording)
        if (status == RECORDING_PAUSED) {
            text += " (${getString(R.string.paused)})"
        }

        if (hideNotification) {
            priority = Notification.PRIORITY_MIN
            icon = R.drawable.ic_empty
            title = ""
            text = ""
            visibility = NotificationCompat.VISIBILITY_SECRET
        }

        val builder = NotificationCompat.Builder(this)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(getOpenAppIntent())
            .setPriority(priority)
            .setVisibility(visibility)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)
            .setChannelId(channelId)

        return builder.build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
        return PendingIntent.getActivity(this, RECORDER_RUNNING_NOTIF_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcastDuration() {
        EventBus.getDefault().post(Events.RecordingDuration(duration))
    }

    private fun broadcastStatus() {
        EventBus.getDefault().post(Events.RecordingStatus(status))
    }
}
