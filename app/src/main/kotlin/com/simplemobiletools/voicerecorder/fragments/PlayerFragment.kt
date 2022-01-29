package com.simplemobiletools.voicerecorder.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.AttributeSet
import android.widget.SeekBar
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.adapters.RecordingsAdapter
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.interfaces.RefreshRecordingsListener
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.fragment_player.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class PlayerFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshRecordingsListener {
    private val FAST_FORWARD_SKIP_MS = 10000

    private var player: MediaPlayer? = null
    private var progressTimer = Timer()
    private var playedRecordingIDs = Stack<Int>()
    private var bus: EventBus? = null
    private var prevSavePath = ""
    private var playOnPreparation = true

    override fun onResume() {
        setupColors()
        if (prevSavePath.isNotEmpty() && context!!.config.saveRecordingsFolder != prevSavePath) {
            setupAdapter()
        } else {
            getRecordingsAdapter()?.updateTextColor(context.config.textColor)
        }

        storePrevPath()
    }

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null

        bus?.unregister(this)
        progressTimer.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        bus = EventBus.getDefault()
        bus!!.register(this)
        setupColors()
        setupAdapter()
        initMediaPlayer()
        setupViews()
        storePrevPath()
    }

    private fun setupViews() {
        play_pause_btn.setOnClickListener {
            if (playedRecordingIDs.empty() || player_progressbar.max == 0) {
                next_btn.callOnClick()
            } else {
                togglePlayPause()
            }
        }

        player_progress_current.setOnClickListener {
            skip(false)
        }

        player_progress_max.setOnClickListener {
            skip(true)
        }

        previous_btn.setOnClickListener {
            if (playedRecordingIDs.isEmpty()) {
                return@setOnClickListener
            }

            val adapter = getRecordingsAdapter() ?: return@setOnClickListener
            var wantedRecordingID = playedRecordingIDs.pop()
            if (wantedRecordingID == adapter.currRecordingId && !playedRecordingIDs.isEmpty()) {
                wantedRecordingID = playedRecordingIDs.pop()
            }

            val prevRecordingIndex = adapter.recordings.indexOfFirst { it.id == wantedRecordingID }
            val prevRecording = adapter.recordings.getOrNull(prevRecordingIndex) ?: return@setOnClickListener
            playRecording(prevRecording, true)
        }

        player_title.setOnLongClickListener {
            if (player_title.value.isNotEmpty()) {
                context.copyToClipboard(player_title.value)
            }
            true
        }

        next_btn.setOnClickListener {
            val adapter = getRecordingsAdapter()
            if (adapter == null || adapter.recordings.isEmpty()) {
                return@setOnClickListener
            }

            val oldRecordingIndex = adapter.recordings.indexOfFirst { it.id == adapter.currRecordingId }
            val newRecordingIndex = (oldRecordingIndex + 1) % adapter.recordings.size
            val newRecording = adapter.recordings.getOrNull(newRecordingIndex) ?: return@setOnClickListener
            playRecording(newRecording, true)
            playedRecordingIDs.push(newRecording.id)
        }
    }

    override fun refreshRecordings() {
        setupAdapter()
    }

    private fun setupAdapter() {
        val recordings = getRecordings()

        recordings_fastscroller.beVisibleIf(recordings.isNotEmpty())
        recordings_placeholder.beVisibleIf(recordings.isEmpty())
        if (recordings.isEmpty()) {
            val stringId = if (isQPlus()) R.string.no_recordings_found else R.string.no_recordings_in_folder_found
            recordings_placeholder.text = context.getString(stringId)
            resetProgress(null)
            player?.stop()
        }

        val adapter = getRecordingsAdapter()
        if (adapter == null) {
            RecordingsAdapter(context as SimpleActivity, recordings, this, recordings_list) {
                playRecording(it as Recording, true)
                if (playedRecordingIDs.isEmpty() || playedRecordingIDs.peek() != it.id) {
                    playedRecordingIDs.push(it.id)
                }
            }.apply {
                recordings_list.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                recordings_list.scheduleLayoutAnimation()
            }
        } else {
            adapter.updateItems(recordings)
        }
    }

    private fun getRecordings(): ArrayList<Recording> {
        return if (isQPlus()) {
            getMediaStoreRecordings()
        } else {
            getLegacyRecordings()
        }
    }

    @SuppressLint("InlinedApi")
    private fun getMediaStoreRecordings(): ArrayList<Recording> {
        val recordings = ArrayList<Recording>()

        val uri = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            Media._ID,
            Media.DISPLAY_NAME,
            Media.DATE_ADDED,
            Media.DURATION,
            Media.SIZE
        )

        val selection = "${Media.OWNER_PACKAGE_NAME} = ?"
        val selectionArgs = arrayOf(context.packageName)
        val sortOrder = "${Media.DATE_ADDED} DESC"

        context.queryCursor(uri, projection, selection, selectionArgs, sortOrder, true) { cursor ->
            val id = cursor.getIntValue(Media._ID)
            val title = cursor.getStringValue(Media.DISPLAY_NAME)
            val timestamp = cursor.getIntValue(Media.DATE_ADDED)
            var duration = cursor.getLongValue(Media.DURATION) / 1000
            var size = cursor.getIntValue(Media.SIZE)

            if (duration == 0L) {
                duration = getDurationFromUri(id.toLong())
            }

            if (size == 0) {
                size = getSizeFromUri(id.toLong())
            }

            val recording = Recording(id, title, "", timestamp, duration.toInt(), size)
            recordings.add(recording)
        }

        return recordings
    }

    private fun getLegacyRecordings(): ArrayList<Recording> {
        val recordings = ArrayList<Recording>()
        val files = File(context.config.saveRecordingsFolder).listFiles() ?: return recordings

        files.filter { it.isAudioFast() }.forEach {
            val id = it.hashCode()
            val title = it.name
            val path = it.absolutePath
            val timestamp = (it.lastModified() / 1000).toInt()
            val duration = context.getDuration(it.absolutePath) ?: 0
            val size = it.length().toInt()
            val recording = Recording(id, title, path, timestamp, duration, size)
            recordings.add(recording)
        }

        recordings.sortByDescending { it.timestamp }
        return recordings
    }

    private fun getDurationFromUri(id: Long): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, getAudioFileContentUri(id))
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
            Math.round(time.toLong() / 1000.toDouble())
        } catch (e: Exception) {
            0L
        }
    }

    private fun getSizeFromUri(id: Long): Int {
        val recordingUri = getAudioFileContentUri(id)
        return try {
            context.contentResolver.openInputStream(recordingUri)?.available() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun initMediaPlayer() {
        player = MediaPlayer().apply {
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)

            setOnCompletionListener {
                progressTimer.cancel()
                player_progressbar.progress = player_progressbar.max
                player_progress_current.text = player_progress_max.text
                play_pause_btn.setImageDrawable(getToggleButtonIcon(false))
            }

            setOnPreparedListener {
                if (playOnPreparation) {
                    setupProgressTimer()
                    player?.start()
                }

                playOnPreparation = true
            }
        }
    }

    override fun playRecording(recording: Recording, playOnPrepared: Boolean) {
        resetProgress(recording)
        (recordings_list.adapter as RecordingsAdapter).updateCurrentRecording(recording.id)
        playOnPreparation = playOnPrepared

        player!!.apply {
            reset()

            try {
                if (isQPlus()) {
                    setDataSource(context, getAudioFileContentUri(recording.id.toLong()))
                } else {
                    setDataSource(recording.path)
                }
            } catch (e: Exception) {
                context?.showErrorToast(e)
                return
            }

            try {
                prepareAsync()
            } catch (e: Exception) {
                context.showErrorToast(e)
                return
            }
        }

        play_pause_btn.setImageDrawable(getToggleButtonIcon(playOnPreparation))
        player_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !playedRecordingIDs.isEmpty()) {
                    player?.seekTo(progress * 1000)
                    player_progress_current.text = progress.getFormattedDuration()
                    resumePlayback()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupProgressTimer() {
        progressTimer.cancel()
        progressTimer = Timer()
        progressTimer.scheduleAtFixedRate(getProgressUpdateTask(), 1000, 1000)
    }

    private fun getProgressUpdateTask() = object : TimerTask() {
        override fun run() {
            Handler(Looper.getMainLooper()).post {
                if (player != null) {
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

    private fun resetProgress(recording: Recording?) {
        updateCurrentProgress(0)
        player_progressbar.progress = 0
        player_progressbar.max = recording?.duration ?: 0
        player_title.text = recording?.title ?: ""
        player_progress_max.text = (recording?.duration ?: 0).getFormattedDuration()
    }

    private fun togglePlayPause() {
        if (getIsPlaying()) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        player?.pause()
        play_pause_btn.setImageDrawable(getToggleButtonIcon(false))
        progressTimer.cancel()
    }

    private fun resumePlayback() {
        player?.start()
        play_pause_btn.setImageDrawable(getToggleButtonIcon(true))
        setupProgressTimer()
    }

    private fun getToggleButtonIcon(isPlaying: Boolean): Drawable {
        val drawable = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        return resources.getColoredDrawableWithColor(drawable, context.getAdjustedPrimaryColor().getContrastColor())
    }

    private fun skip(forward: Boolean) {
        if (playedRecordingIDs.empty()) {
            return
        }

        val curr = player?.currentPosition ?: return
        var newProgress = if (forward) curr + FAST_FORWARD_SKIP_MS else curr - FAST_FORWARD_SKIP_MS
        if (newProgress > player!!.duration) {
            newProgress = player!!.duration
        }

        player!!.seekTo(newProgress)
        resumePlayback()
    }

    private fun getIsPlaying() = player?.isPlaying == true

    private fun getRecordingsAdapter() = recordings_list.adapter as? RecordingsAdapter

    private fun storePrevPath() {
        prevSavePath = context!!.config.saveRecordingsFolder
    }

    private fun setupColors() {
        val adjustedPrimaryColor = context.getAdjustedPrimaryColor()
        recordings_fastscroller.updateColors(adjustedPrimaryColor)
        context.updateTextColors(player_holder)

        val textColor = context.config.textColor
        arrayListOf(previous_btn, next_btn).forEach {
            it.applyColorFilter(textColor)
        }

        play_pause_btn.background.applyColorFilter(adjustedPrimaryColor)
        play_pause_btn.setImageDrawable(getToggleButtonIcon(false))
    }

    fun finishActMode() = getRecordingsAdapter()?.finishActMode()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun recordingCompleted(event: Events.RecordingCompleted) {
        refreshRecordings()
    }
}
