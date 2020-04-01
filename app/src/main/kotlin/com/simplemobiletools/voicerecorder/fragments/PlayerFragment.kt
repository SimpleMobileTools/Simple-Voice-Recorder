package com.simplemobiletools.voicerecorder.fragments

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.provider.MediaStore
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.adapters.RecordingsAdapter
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.fragment_player.view.*

class PlayerFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var player: MediaPlayer? = null

    override fun onResume() {
        setupColors()
    }

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val recordings = getRecordings()
        RecordingsAdapter(context as SimpleActivity, recordings, recordings_list, recordings_fastscroller) {
            playRecording(it as Recording)
        }.apply {
            recordings_list.adapter = this
        }

        recordings_fastscroller.setScrollToY(0)
        recordings_fastscroller.setViews(recordings_list) {
            val item = (recordings_list.adapter as RecordingsAdapter).recordings.getOrNull(it)
            recordings_fastscroller.updateBubbleText(item?.title ?: "")
        }

        initMediaPlayer()
    }

    @SuppressLint("InlinedApi")
    private fun getRecordings(): ArrayList<Recording> {
        val recordings = ArrayList<Recording>()

        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME} = ?"
        val selectionArgs = arrayOf(context.packageName)
        val sorting = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sorting)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(MediaStore.Audio.Media._ID)
                    val title = cursor.getStringValue(MediaStore.Audio.Media.TITLE)
                    val path = ""
                    val timestamp = cursor.getIntValue(MediaStore.Audio.Media.DATE_ADDED)
                    val duration = cursor.getLongValue(MediaStore.Audio.Media.DURATION) / 1000
                    val size = cursor.getIntValue(MediaStore.Audio.Media.SIZE)
                    val recording = Recording(id, title, "", timestamp, duration.toInt(), size)
                    recordings.add(recording)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return recordings
    }

    private fun initMediaPlayer() {
        player = MediaPlayer().apply {
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }

    private fun playRecording(recording: Recording) {
        val recordingUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            recording.id.toLong()
        )

        updateCurrentProgress(0)
        player_title.text = recording.title
        player_progress_max.text = recording.duration.getFormattedDuration()

        player!!.apply {
            reset()
            setDataSource(context, recordingUri)
            prepare()
            start()
        }
    }

    private fun updateCurrentProgress(seconds: Int) {
        player_progress_current.text = seconds.getFormattedDuration()
    }

    private fun setupColors() {
        recordings_fastscroller.updatePrimaryColor()
        recordings_fastscroller.updateBubbleColors()
    }
}
