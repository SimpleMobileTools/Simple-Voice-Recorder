package com.simplemobiletools.voicerecorder.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import com.simplemobiletools.commons.dialogs.ColorPickerDialog
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.setFillWithStroke
import com.simplemobiletools.commons.helpers.DEFAULT_WIDGET_BG_COLOR
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.MyWidgetRecordDisplayProvider
import kotlinx.android.synthetic.main.widget_record_display_config.*

class WidgetRecordDisplayConfigureActivity : SimpleActivity() {
    private var mWidgetAlpha = 0f
    private var mWidgetId = 0
    private var mWidgetColor = 0
    private var mWidgetColorWithoutTransparency = 0

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_record_display_config)
        initVariables()

        val isCustomizingColors = intent.extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isCustomizingColors) {
            finish()
        }

        config_save.setOnClickListener { saveConfig() }
        config_widget_color.setOnClickListener { pickBackgroundColor() }
    }

    private fun initVariables() {
        mWidgetColor = resources.getColor(R.color.color_primary)
        mWidgetAlpha = if (mWidgetColor == DEFAULT_WIDGET_BG_COLOR) {
            1f
        } else {
            Color.alpha(mWidgetColor) / 255.toFloat()
        }

        mWidgetColorWithoutTransparency = Color.rgb(Color.red(mWidgetColor), Color.green(mWidgetColor), Color.blue(mWidgetColor))
        config_widget_seekbar.setOnSeekBarChangeListener(seekbarChangeListener)
        config_widget_seekbar.progress = (mWidgetAlpha * 100).toInt()
        updateColors()
    }

    private fun saveConfig() {
        config.widgetBgColor = mWidgetColor
        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mWidgetColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mWidgetColorWithoutTransparency = color
                updateColors()
            }
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetRecordDisplayProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateColors() {
        mWidgetColor = mWidgetColorWithoutTransparency.adjustAlpha(mWidgetAlpha)
        config_widget_color.setFillWithStroke(mWidgetColor, Color.BLACK)
        config_image.background.mutate().applyColorFilter(mWidgetColor)
    }

    private val seekbarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            mWidgetAlpha = progress.toFloat() / 100.toFloat()
            updateColors()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }
}
