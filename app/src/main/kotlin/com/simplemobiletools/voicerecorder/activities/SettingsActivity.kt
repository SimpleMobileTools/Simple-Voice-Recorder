package com.simplemobiletools.voicerecorder.activities

import android.os.Bundle
import com.simplemobiletools.commons.dialogs.ChangeDateTimeFormatDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.AUDIO_SOURCE
import com.simplemobiletools.voicerecorder.helpers.BITRATES
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_M4A
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_MP3
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_OGG
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupChangeDateTimeFormat()
        setupHideNotification()
        setupSaveRecordingsFolder()
        setupExtension()
        setupBitrate()
        setupAudioSource()
        setupRecordAfterLaunch()
        updateTextColors(settings_nested_scrollview)

        arrayOf(settings_color_customization_label, settings_general_settings_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }

        arrayOf(settings_color_customization_holder, settings_general_settings_holder).forEach {
            it.background.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())

        // make sure the corners at ripple fit the stroke rounded corners
        if (settings_purchase_thank_you_holder.isGone()) {
            settings_use_english_holder.background = resources.getDrawable(R.drawable.ripple_top_corners, theme)
            settings_language_holder.background = resources.getDrawable(R.drawable.ripple_top_corners, theme)
        }

        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_label.text = getCustomizeColorsString()
        settings_customize_colors_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())

        if (settings_use_english_holder.isGone() && settings_language_holder.isGone() && settings_purchase_thank_you_holder.isGone()) {
            settings_change_date_time_format_holder.background = resources.getDrawable(R.drawable.ripple_top_corners, theme)
        }

        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupHideNotification() {
        settings_hide_notification.isChecked = config.hideNotification
        settings_hide_notification_holder.setOnClickListener {
            settings_hide_notification.toggle()
            config.hideNotification = settings_hide_notification.isChecked
        }
    }

    private fun setupSaveRecordingsFolder() {
        settings_save_recordings.text = humanizePath(config.saveRecordingsFolder)
        settings_save_recordings_holder.setOnClickListener {
            FilePickerDialog(this, config.saveRecordingsFolder, false, showFAB = true) {
                val path = it
                handleSAFDialog(path) { grantedSAF ->
                    if (!grantedSAF) {
                        return@handleSAFDialog
                    }

                    handleSAFDialogSdk30(path) { grantedSAF30 ->
                        if (!grantedSAF30) {
                            return@handleSAFDialogSdk30
                        }

                        config.saveRecordingsFolder = path
                        settings_save_recordings.text = humanizePath(config.saveRecordingsFolder)
                    }
                }
            }
        }
    }

    private fun setupExtension() {
        settings_extension.text = config.getExtensionText()
        settings_extension_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(EXTENSION_M4A, getString(R.string.m4a)),
                RadioItem(EXTENSION_MP3, getString(R.string.mp3))
            )

            if (isQPlus()) {
                items.add(RadioItem(EXTENSION_OGG, getString(R.string.ogg)))
            }

            RadioGroupDialog(this@SettingsActivity, items, config.extension) {
                config.extension = it as Int
                settings_extension.text = config.getExtensionText()
            }
        }
    }

    private fun setupBitrate() {
        settings_bitrate.text = getBitrateText(config.bitrate)
        settings_bitrate_holder.setOnClickListener {
            val items = BITRATES.map { RadioItem(it, getBitrateText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.bitrate) {
                config.bitrate = it as Int
                settings_bitrate.text = getBitrateText(config.bitrate)
            }
        }
    }

    private fun getBitrateText(value: Int): String = getString(R.string.bitrate_value).format(value / 1000)

    private fun setupRecordAfterLaunch() {
        settings_record_after_launch.isChecked = config.recordAfterLaunch
        settings_record_after_launch_holder.setOnClickListener {
            settings_record_after_launch.toggle()
            config.recordAfterLaunch = settings_record_after_launch.isChecked
        }
    }

    private fun setupAudioSource() {
        settings_audio_source.text = config.getAudioSourceText(config.audio_source)
        settings_audio_source_holder.setOnClickListener {
            val items = AUDIO_SOURCE.map { RadioItem(it, config.getAudioSourceText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.audio_source) {
                config.audio_source = it as Int
                settings_audio_source.text = config.getAudioSourceText(config.audio_source)
            }
        }
    }
}
