package com.simplemobiletools.voicerecorder.fragments

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.widget.SeekBar
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isOnMainThread
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.adapters.RecordingsAdapter
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.fragment_player.view.*
import java.util.*
import kotlin.collections.ArrayList

class PlayerFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private val FAST_FORWARD_SKIP_MS = 10000

    private var player: MediaPlayer? = null
    private var progressTimer = Timer()

    override fun onResume() {
        setupColors()
    }

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null

        progressTimer.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setupColors()
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

        play_pause_btn.setOnClickListener {
            togglePlayPause()
        }

        player_progress_current.setOnClickListener {
            skip(false)
        }

        player_progress_max.setOnClickListener {
            skip(true)
        }
    }

    @SuppressLint("InlinedApi")
    private fun getRecordings(): ArrayList<Recording> {
        val recordings = ArrayList<Recording>()

        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
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
                    val title = cursor.getStringValue(MediaStore.Audio.Media.DISPLAY_NAME)
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

            setOnCompletionListener {
                progressTimer.cancel()
                player_progressbar.progress = player_progressbar.max
                player_progress_current.text = player_progress_max.text
            }

            setOnPreparedListener {
                setupProgressTimer()
                player?.start()
            }
        }
    }

    private fun playRecording(recording: Recording) {
        val recordingUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            recording.id.toLong()
        )

        updateCurrentProgress(0)
        player_progressbar.progress = 0
        player_progressbar.max = recording.duration
        player_title.text = recording.title
        player_progress_max.text = recording.duration.getFormattedDuration()

        player!!.apply {
            reset()
            setDataSource(context, recordingUri)
            prepare()
        }

        songStateChanged(true)

        player_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress * 1000)
                    player_progress_current.text = progress.getFormattedDuration()
                    resumeSong()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupProgressTimer() {
        progressTimer.cancel()
        progressTimer = Timer()
        progressTimer.scheduleAtFixedRate(getProgressUpdateTask(), 0, 1000)
    }

    private fun getProgressUpdateTask() = object : TimerTask() {
        override fun run() {
            if (player != null) {
                Handler(Looper.getMainLooper()).post {
                    val progress = Math.round(player!!.currentPosition / 1000.toDouble()).toInt()
                    updateCurrentProgress(progress)
                    player_progressbar.progress = progress
                }
            }
        }
    }

    private fun updateCurrentProgress(seconds: Int) {
        player_progress_current.text = seconds.getFormattedDuration()
    }

    private fun togglePlayPause() {
        if (getIsPlaying()) {
            pauseSong()
        } else {
            resumeSong()
        }
    }

    private fun pauseSong() {
        player?.pause()
        songStateChanged(false)
        progressTimer.cancel()
    }

    private fun resumeSong() {
        player?.start()
        songStateChanged(true)
        setupProgressTimer()
    }

    private fun songStateChanged(isPlaying: Boolean) {
        val drawable = resources.getDrawable(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
        play_pause_btn.setImageDrawable(drawable)
    }

    private fun skip(forward: Boolean) {
        val curr = player?.currentPosition ?: return
        var newProgress = if (forward) curr + FAST_FORWARD_SKIP_MS else curr - FAST_FORWARD_SKIP_MS
        if (newProgress > player!!.duration) {
            newProgress = player!!.duration
        }

        player!!.seekTo(newProgress)
        resumeSong()
    }

    private fun getIsPlaying() = player?.isPlaying == true

    private fun setupColors() {
        recordings_fastscroller.updatePrimaryColor()
        recordings_fastscroller.updateBubbleColors()
        context.updateTextColors(player_controls_wrapper)

        val textColor = context.config.textColor
        arrayListOf(previous_btn, play_pause_btn, next_btn).forEach {
            it.applyColorFilter(textColor)
        }
    }
}
