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
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.databinding.ActivityEditRecordingBinding
import com.simplemobiletools.voicerecorder.extensions.addFileInLegacyMediaStore
import com.simplemobiletools.voicerecorder.extensions.addFileInNewMediaStore
import com.simplemobiletools.voicerecorder.extensions.getAllRecordings
import com.simplemobiletools.voicerecorder.extensions.getBaseFolder
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import linc.com.amplituda.Amplituda
import linc.com.amplituda.AmplitudaResult
import linc.com.amplituda.callback.AmplitudaSuccessListener
import linc.com.library.AudioTool
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.OutputStream
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
        val controls = listOf(
            binding.playerControlsWrapper.trimBtn,
            binding.playerControlsWrapper.cutBtn,
            binding.playerControlsWrapper.clearBtn,
            binding.playerControlsWrapper.resetBtn
        )
        controls.forEach {
            it.isVisible = false
        }

        binding.playerControlsWrapper.trimBtn.setOnClickListener { trimSelection() }
        binding.playerControlsWrapper.cutBtn.setOnClickListener { cutSelection() }
        binding.playerControlsWrapper.clearBtn.setOnClickListener { clearSelection() }
        binding.playerControlsWrapper.resetBtn.setOnClickListener { resetEditing() }

        binding.recordingVisualizer.chunkColor = getProperPrimaryColor()
        binding.recordingVisualizer.recreate()
        binding.recordingVisualizer.editListener = {
            if (binding.recordingVisualizer.startPosition >= 0f) {
                playSelection()
                controls.forEach { it.isVisible = true }
            } else {
                controls.forEach { it.isVisible = false }
                if (recording != currentRecording) {
                    binding.playerControlsWrapper.resetBtn.isVisible = true
                }
            }

            if (recording != currentRecording) {
                binding.editToolbar.menu.forEach { it.isVisible = true }
            } else {
                binding.editToolbar.menu.forEach { it.isVisible = false }
            }

            binding.playerControlsWrapper.selectedStartTime.setText(binding.recordingVisualizer.startPosition.formatSelectionPosition())
            binding.playerControlsWrapper.selectedEndTime.setText(binding.recordingVisualizer.endPosition.formatSelectionPosition())
        }
        updateVisualization()

        initMediaPlayer()
        playRecording(recording.path, recording.id, recording.duration)

        binding.playerControlsWrapper.playPauseBtn.setOnClickListener {
            togglePlayPause()
        }
        binding.playerControlsWrapper.selectedStartTime.setText(0f.formatSelectionPosition())
        binding.playerControlsWrapper.selectedEndTime.setText(1f.formatSelectionPosition())

//        binding.playerControlsWrapper.selectedStartTime.onTextChangeListener {
//
//        }
        setupColors()
    }

    private fun setupOptionsMenu() {
        binding.editToolbar.inflateMenu(R.menu.edit_menu)

        binding.editToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> saveChanges()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
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
        listOf(
            binding.playerControlsWrapper.trimBtn,
            binding.playerControlsWrapper.cutBtn,
            binding.playerControlsWrapper.clearBtn,
            binding.playerControlsWrapper.resetBtn
        ).forEach {
            it.applyColorFilter(textColor)
        }

        binding.playerControlsWrapper.playPauseBtn.background.applyColorFilter(properPrimaryColor)
        binding.playerControlsWrapper.playPauseBtn.setImageDrawable(getToggleButtonIcon(false))
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
        }
    }

    fun playRecording(path: String, id: Int?, duration: Int?) {
        resetProgress(duration)

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

    private fun clearSelection() {
        progressStart = 0f
        binding.recordingVisualizer.clearEditing()
        playRecording(currentRecording.path, currentRecording.id, currentRecording.duration)
    }

    private fun resetEditing() {
        progressStart = 0f
        binding.recordingVisualizer.clearEditing()
        currentRecording = recording
        updateVisualization()
        playRecording(currentRecording.path, currentRecording.id, currentRecording.duration)
    }

    private fun playSelection() {
        val start = binding.recordingVisualizer.startPosition
        val end = binding.recordingVisualizer.endPosition

        val durationMillis = (end - start) * currentRecording.duration
        val startFormatted = start.formatSelectionPosition()
        val durationFormatted = (end - start).formatSelectionPosition()
        if (durationMillis > 0f) {
            modifyAudioFile(currentRecording)
                .cutAudio(startFormatted, durationFormatted) {
                    progressStart = binding.recordingVisualizer.startPosition
                    playRecording(it.path, null, durationMillis.toInt())
                }
        }
    }

    private fun trimSelection() {
        val start = binding.recordingVisualizer.startPosition
        val end = binding.recordingVisualizer.endPosition

        val durationMillis = (end - start) * currentRecording.duration
        val startFormatted = start.formatSelectionPosition()
        val durationFormatted = (end - start).formatSelectionPosition()
        modifyAudioFile(currentRecording)
            .cutAudio(startFormatted, durationFormatted) {
                runOnUiThread {
                    currentRecording = Recording(-1, it.name, it.path, it.lastModified().toInt(), durationMillis.toInt(), it.getProperSize(false).toInt())
                    updateVisualization()
                    playRecording(currentRecording.path, currentRecording.id, currentRecording.duration)
                }
            }
    }

    private fun cutSelection() {
        val start = binding.recordingVisualizer.startPosition
        val end = binding.recordingVisualizer.endPosition

        val startMillis = start * currentRecording.duration
        val realEnd = (1 - end) * currentRecording.duration
        val startFormatted = start.formatSelectionPosition()
        val endFormatted = end.formatSelectionPosition()
        val realEndFormatted = (1 - end).formatSelectionPosition()

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
                                playRecording(currentRecording.path, currentRecording.id, currentRecording.duration)
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

    private fun saveChanges() {
        val finalLocation = "${getBaseFolder()}/${recording.title}.edit.${recording.title.getFilenameExtension()}"
        if (isRPlus() && hasProperStoredFirstParentUri(finalLocation)) {
            val fileUri = createDocumentUriUsingFirstParentTreeUri(finalLocation)
            createSAFFileSdk30(finalLocation)
            val outputStream = contentResolver.openOutputStream(fileUri, "w")!!
            copyTo(currentRecording, outputStream)
        } else if (!isRPlus() && isPathOnSD(finalLocation)) {
            var document = getDocumentFile(finalLocation.getParentPath())
            document = document?.createFile("", finalLocation.getFilenameFromPath())
            val outputStream = contentResolver.openOutputStream(document!!.uri, "w")!!
            copyTo(currentRecording, outputStream)
        } else {
            copyTo(currentRecording, File(finalLocation).outputStream())
        }


        if (isRPlus() && !hasProperStoredFirstParentUri(finalLocation)) {
            addFileInNewMediaStore(finalLocation) {
                toast(R.string.recording_saved_successfully)
                AudioTool.getInstance(this).release()
                getRecordingsCache().listFiles()?.forEach { it.delete() }
                EventBus.getDefault().post(Events.RecordingEdited())
                finish()
            }
        } else {
            addFileInLegacyMediaStore(finalLocation) {
                toast(R.string.recording_saved_successfully)
                AudioTool.getInstance(this).release()
                getRecordingsCache().listFiles()?.forEach { it.delete() }
                EventBus.getDefault().post(Events.RecordingEdited())
                finish()
            }
        }
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

    private fun resetProgress(duration: Int?) {
        updateCurrentProgress(0)
        binding.playerControlsWrapper.playerProgressbar.progress = 0
        binding.playerControlsWrapper.playerProgressbar.max = duration ?: 0
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

    private fun copyTo(recording: Recording, outputStream: OutputStream) {
        try {
            val uri = Uri.parse(recording.path)

            when {
                DocumentsContract.isDocumentUri(this@EditRecordingActivity, uri) -> {
                    contentResolver.openInputStream(uri)?.copyTo(outputStream)
                }

                recording.path.isEmpty() -> {
                    contentResolver.openInputStream(getAudioFileContentUri(recording.id.toLong()))?.copyTo(outputStream)
                }

                else -> {
                    File(recording.path).inputStream().copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
            File(recording.path).inputStream().copyTo(outputStream)
        }
    }

    private fun copyToTempFile(recording: Recording): File {
        try {
            val recordingCacheDir = getRecordingsCache()
            val uri = Uri.parse(recording.path)

            when {
                DocumentsContract.isDocumentUri(this@EditRecordingActivity, uri) -> {
                    val tempFile = File.createTempFile(recording.title, ".${recording.title.getFilenameExtension()}", recordingCacheDir)
                    contentResolver.openInputStream(uri)?.copyTo(tempFile.outputStream())
                    return tempFile
                }

                recording.path.isEmpty() -> {
                    val tempFile = File.createTempFile(recording.title, ".${recording.title.getFilenameExtension()}", recordingCacheDir)
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

    private fun getRecordingsCache() = File(cacheDir, "tmp_recordings")

    private fun Float.formatSelectionPosition(): String {
        val millis = this * currentRecording.duration
        val millisPart = String.format("%.3f", millis - millis.toInt()).replace("0.", "")
        return (millis.toInt()).getFormattedDuration(true) + ".$millisPart"
    }
}
