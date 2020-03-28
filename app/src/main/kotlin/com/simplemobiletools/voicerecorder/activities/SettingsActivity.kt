package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.isThankYouInstalled
import com.simplemobiletools.commons.extensions.launchPurchaseThankYouIntent
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.voicerecorder.R
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        updateTextColors(settings_scrollview)
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beVisibleIf(!isThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }
}
