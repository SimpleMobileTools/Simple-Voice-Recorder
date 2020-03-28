package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_RECORD_AUDIO
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.voicerecorder.BuildConfig
import com.simplemobiletools.voicerecorder.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    private var isRecording = false

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
        val filename = "${getCurrentFormattedDateTime()}.mp3"
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

    private fun startRecording() {

    }

    private fun stopRecording() {

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
