package com.simplemobiletools.voicerecorder.activities

import android.content.ContentValues
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_RECORD_AUDIO
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.voicerecorder.BuildConfig
import com.simplemobiletools.voicerecorder.R
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : SimpleActivity() {
    private var isRecording = false
    private var recorder: MediaRecorder? = null
    private var currFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (checkAppSideloading()) {
            return
        }

        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                initVoiceRecorder()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        toggle_recording_button.apply {
            setImageDrawable(getToggleButtonIcon())
            background.applyColorFilter(getAdjustedPrimaryColor())
        }
    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initVoiceRecorder() {
        toggle_recording_button.setOnClickListener {
            toggleRecording()
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        toggle_recording_button.setImageDrawable(getToggleButtonIcon())

        if (isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    // mp4 output format with aac encoding should produce good enough mp3 files according to https://stackoverflow.com/a/33054794/1967672
    private fun startRecording() {
        currFilePath = "${cacheDir}/${getCurrentFormattedDateTime()}.mp3"
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currFilePath)

            try {
                prepare()
            } catch (e: IOException) {
                showErrorToast(e)
            }

            start()
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
            ensureBackgroundThread {
                addFileInMediaStore()
            }
        }
        recorder = null
    }

    private fun addFileInMediaStore() {
        val audioCollection = if (isQPlus()) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

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
    }

    private fun getToggleButtonIcon(): Drawable {
        val drawable = if (isRecording) R.drawable.ic_stop_vector else R.drawable.ic_mic_vector
        return resources.getColoredDrawableWithColor(drawable, getFABIconColor())
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = 0

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
