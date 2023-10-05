package com.simplemobiletools.voicerecorder.activities

import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.DocumentsContract
import android.widget.SeekBar
import androidx.core.view.children
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.databinding.ActivityEditRecordingBinding
import com.simplemobiletools.voicerecorder.extensions.getAllRecordings
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.models.Recording
import linc.com.amplituda.Amplituda
import linc.com.amplituda.AmplitudaResult
import linc.com.amplituda.callback.AmplitudaSuccessListener
import linc.com.library.AudioTool
import java.io.File
import java.util.Timer
import java.util.TimerTask

class EditRecordingActivity : SimpleActivity() {
    companion object {
        const val RECORDING_ID = "recording_id"
    }

    private var player: MediaPlayer? = null
    private var progressTimer = Timer()
    private lateinit var recording: Recording
    private lateinit var currentRecording: Recording
    private var progressStart: Float = 0f

    private lateinit var binding: ActivityEditRecordingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityEditRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupOptionsMenu()

        updateMaterialActivityViews(binding.mainCoordinator, binding.recordingVisualizer, useTransparentNavigation = false, useTopSearchMenu = false)

        val recordingId = intent.getIntExtra(RECORDING_ID, -1)
        if (recordingId == -1) {
            finish()
            return
        }

        recording = getAllRecordings().first { it.id == recordingId }
        currentRecording = recording
//        AudioTool.getInstance(this)
//            .withAudio(File(recording.path))
//            .cutAudio("00:00:00", "00:00:00.250") {}
//            .saveCurrentTo(recording.path)
//            .release()

//        binding.recordingVisualizer.waveProgressColor = getProperPrimaryColor()
//        binding.recordingVisualizer.setSampleFrom(recording.path)
        binding.recordingVisualizer.chunkColor = getProperPrimaryColor()
        binding.recordingVisualizer.recreate()
        binding.recordingVisualizer.editListener = {
            if (binding.recordingVisualizer.startPosition >= 0f) {
                binding.editToolbar.menu.children.forEach { it.isVisible = true }
            } else {
                binding.editToolbar.menu.children.forEach { it.isVisible = false }
            }
        }
        updateVisualization()
//        android.media.MediaCodec.createByCodecName().createInputSurface()
//        binding.recordingVisualizer.update()

        initMediaPlayer()
        playRecording(recording.path, recording.id, recording.title, recording.duration, false)

        binding.playerControlsWrapper.playPauseBtn.setOnClickListener {
            togglePlayPause()
        }
        setupColors()
    }

    private fun updateVisualization() {
        Amplituda(this).apply {
            try {
                val uri = Uri.parse(currentRecording.path)

                fun handleAmplitudes(amplitudaResult: AmplitudaResult<*>) {
                    binding.recordingVisualizer.recreate()
                    binding.recordingVisualizer.clearEditing()
                    binding.recordingVisualizer.putAmplitudes(amplitudaResult.amplitudesAsList())
                }

                when {
                    DocumentsContract.isDocumentUri(this@EditRecordingActivity, uri) -> {
                        processAudio(contentResolver.openInputStream(uri)).get(AmplitudaSuccessListener {
                            handleAmplitudes(it)
                        })
                    }

                    currentRecording.path.isEmpty() -> {
                        processAudio(contentResolver.openInputStream(getAudioFileContentUri(currentRecording.id.toLong()))).get(AmplitudaSuccessListener {
                            handleAmplitudes(it)
                        })
                    }

                    else -> {
                        processAudio(currentRecording.path).get(AmplitudaSuccessListener {
                            handleAmplitudes(it)
                        })
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
                return
            }
        }
    }

    private fun setupColors() {
        val properPrimaryColor = getProperPrimaryColor()
        updateTextColors(binding.mainCoordinator)

        val textColor = getProperTextColor()
        arrayListOf(binding.playerControlsWrapper.previousBtn, binding.playerControlsWrapper.nextBtn).forEach {
            it.applyColorFilter(textColor)
        }

        binding.playerControlsWrapper.playPauseBtn.background.applyColorFilter(properPrimaryColor)
        binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(false))
    }

    private fun setupOptionsMenu() {
        binding.editToolbar.inflateMenu(R.menu.menu_edit)
//        binding.settingsToolbar.toggleHideOnScroll(false)
//        binding.settingsToolbar.setupMenu()

//        binding.settingsToolbar.onSearchOpenListener = {
//            if (binding.viewPager.currentItem == 0) {
//                binding.viewPager.currentItem = 1
//            }
//        }

//        binding.settingsToolbar.onSearchTextChangedListener = { text ->
//            getPagerAdapter()?.searchTextChanged(text)
//        }

        binding.editToolbar.menu.children.forEach { it.isVisible = false }
        binding.editToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.play -> {
                    val start = binding.recordingVisualizer.startPosition
                    val end = binding.recordingVisualizer.endPosition

                    val startMillis = start * currentRecording.duration
                    val durationMillis = (end - start) * currentRecording.duration
                    val startMillisPart = String.format("%.3f", startMillis - startMillis.toInt()).replace("0.", "")
                    val durationMillisPart = String.format("%.3f", durationMillis - durationMillis.toInt()).replace("0.", "")
                    val startFormatted = (startMillis.toInt()).getFormattedDuration(true) + ".$startMillisPart"
                    val durationFormatted = (durationMillis.toInt()).getFormattedDuration(true) + ".$durationMillisPart"
                    modifyAudioFile(currentRecording)
                        .cutAudio(startFormatted, durationFormatted) {
                            progressStart = binding.recordingVisualizer.startPosition
                            playRecording(it.path, null, it.name, durationMillis.toInt(), true)
                        }
                        .release()
//                    playRecording()
                }
                R.id.cut -> {
                    val start = binding.recordingVisualizer.startPosition
                    val end = binding.recordingVisualizer.endPosition

                    val startMillis = start * currentRecording.duration
                    val endMillis = end * currentRecording.duration
                    val realEnd = (1 - end) * currentRecording.duration
                    val startMillisPart = String.format("%.3f", startMillis - startMillis.toInt()).replace("0.", "")
                    val endMillisPart = String.format("%.3f", endMillis - endMillis.toInt()).replace("0.", "")
                    val realEndMillisPart = String.format("%.3f", realEnd - realEnd.toInt()).replace("0.", "")
                    val startFormatted = (startMillis.toInt()).getFormattedDuration(true) + ".$startMillisPart"
                    val endFormatted = (endMillis.toInt()).getFormattedDuration(true) + ".$endMillisPart"
                    val realEndFormatted = (realEnd.toInt()).getFormattedDuration(true) + ".$realEndMillisPart"

                    var leftPart: File? = null
                    var rightPart: File? = null

                    fun merge() {
                        if (leftPart != null && rightPart != null) {
                            ensureBackgroundThread {
                                val tempFile = File.createTempFile("${currentRecording.title}.edit.", ".${currentRecording.title.getFilenameExtension()}", cacheDir)
                                AudioTool.getInstance(this)
                                    .joinAudios(arrayOf(leftPart, rightPart), tempFile.path) {
                                        runOnUiThread {
                                            currentRecording = Recording(-1, it.name, it.path, it.lastModified().toInt(), (startMillis + realEnd).toInt(), it.getProperSize(false).toInt())
                                            updateVisualization()
                                            playRecording(currentRecording.path, currentRecording.id, currentRecording.title, currentRecording.duration, true)
                                        }
                                    }
                            }
                        }
                    }

                    modifyAudioFile(currentRecording)
                        .cutAudio("00:00:00", startFormatted) {
                            leftPart = it
                            merge()
                        }
                    modifyAudioFile(currentRecording)
                        .cutAudio(endFormatted, realEndFormatted) {
                            rightPart = it
                            merge()
                        }
                }
//                R.id.save -> {
//                    binding.recordingVisualizer.clearEditing()
//                    currentRecording = recording
//                    playRecording(currentRecording.path, currentRecording.id, currentRecording.title, currentRecording.duration, true)
//                }
                R.id.clear -> {
                    progressStart = 0f
                    binding.recordingVisualizer.clearEditing()
                    playRecording(currentRecording.path, currentRecording.id, currentRecording.title, currentRecording.duration, true)
                }
                R.id.reset -> {
                    progressStart = 0f
                    binding.recordingVisualizer.clearEditing()
                    currentRecording = recording
                    updateVisualization()
                    playRecording(currentRecording.path, currentRecording.id, currentRecording.title, currentRecording.duration, true)
                }
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun initMediaPlayer() {
        player = MediaPlayer().apply {
            setWakeMode(this@EditRecordingActivity, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)

            setOnCompletionListener {
                progressTimer.cancel()
                binding.playerControlsWrapper.playerProgressbar.progress = binding.playerControlsWrapper.playerProgressbar.max
                binding.playerControlsWrapper.playerProgressCurrent.text = binding.playerControlsWrapper.playerProgressMax.text
                binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(false))
            }

            setOnPreparedListener {
//                setupProgressTimer()
//                player?.start()
            }
        }
    }

    fun playRecording(path: String, id: Int?, title: String?, duration: Int?, playOnPrepared: Boolean) {
        resetProgress(title, duration)
//        (binding.recordingsList.adapter as RecordingsAdapter).updateCurrentRecording(recording.id)
//        playOnPreparation = playOnPrepared

        player!!.apply {
            reset()

            try {
                val uri = Uri.parse(path)
                when {
                    DocumentsContract.isDocumentUri(this@EditRecordingActivity, uri) -> {
                        setDataSource(this@EditRecordingActivity, uri)
                    }

                    path.isEmpty() -> {
                        setDataSource(this@EditRecordingActivity, getAudioFileContentUri(id?.toLong() ?: 0))
                    }

                    else -> {
                        setDataSource(path)
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
                return
            }

            try {
                prepareAsync()
            } catch (e: Exception) {
                showErrorToast(e)
                return
            }
        }

        binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(false))
        binding.playerControlsWrapper.playerProgressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress * 1000)
                    binding.playerControlsWrapper.playerProgressCurrent.text = progress.getFormattedDuration()
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
        progressTimer.scheduleAtFixedRate(getProgressUpdateTask(), 100, 100)
    }

    private fun getProgressUpdateTask() = object : TimerTask() {
        override fun run() {
            Handler(Looper.getMainLooper()).post {
                if (player != null) {
                    binding.recordingVisualizer.updateProgress(player!!.currentPosition.toFloat() / (currentRecording.duration * 1000) + progressStart)
                    val progress = Math.round(player!!.currentPosition / 1000.toDouble()).toInt()
                    updateCurrentProgress(progress)
                    binding.playerControlsWrapper.playerProgressbar.progress = progress
                }
            }
        }
    }

    private fun updateCurrentProgress(seconds: Int) {
        binding.playerControlsWrapper.playerProgressCurrent.text = seconds.getFormattedDuration()
    }

    private fun resetProgress(title: String?, duration: Int?) {
        updateCurrentProgress(0)
        binding.playerControlsWrapper.playerProgressbar.progress = 0
        binding.playerControlsWrapper.playerProgressbar.max = duration ?: 0
        binding.playerControlsWrapper.playerTitle.text = title ?: ""
        binding.playerControlsWrapper.playerProgressMax.text = (duration ?: 0).getFormattedDuration()
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
        binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(false))
        progressTimer.cancel()
    }

    private fun resumePlayback() {
        player?.start()
        binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(true))
        setupProgressTimer()
    }

    private fun getToggleButtonIcon(isPlaying: Boolean): Drawable {
        val drawable = if (isPlaying) com.simplemobiletools.commons.R.drawable.ic_pause_vector else com.simplemobiletools.commons.R.drawable.ic_play_vector
        return resources.getColoredDrawableWithColor(drawable, getProperPrimaryColor().getContrastColor())
    }

    private fun skip(forward: Boolean) {
//        val curr = player?.currentPosition ?: return
//        var newProgress = if (forward) curr + FAST_FORWARD_SKIP_MS else curr - FAST_FORWARD_SKIP_MS
//        if (newProgress > player!!.duration) {
//            newProgress = player!!.duration
//        }
//
//        player!!.seekTo(newProgress)
//        resumePlayback()
    }

    private fun getIsPlaying() = player?.isPlaying == true

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.editToolbar, NavigationIcon.Arrow)
    }

    override fun onPause() {
        super.onPause()
    }

    private fun modifyAudioFile(recording: Recording): AudioTool {
        return AudioTool.getInstance(this)
            .withAudio(copyToTempFile(recording))
    }

    private fun copyToTempFile(recording: Recording): File {
        try {
            val uri = Uri.parse(recording.path)

            when {
                DocumentsContract.isDocumentUri(this@EditRecordingActivity, uri) -> {
                    val tempFile = File.createTempFile(recording.title, ".${recording.title.getFilenameExtension()}", cacheDir)
                    contentResolver.openInputStream(uri)?.copyTo(tempFile.outputStream())
                    return tempFile
                }

                recording.path.isEmpty() -> {
                    val tempFile = File.createTempFile(recording.title, ".${recording.title.getFilenameExtension()}", cacheDir)
                    contentResolver.openInputStream(getAudioFileContentUri(recording.id.toLong()))?.copyTo(tempFile.outputStream())
                    return tempFile
                }

                else -> {
                    return File(recording.path)
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
            return File(recording.path)
        }
    }
}
