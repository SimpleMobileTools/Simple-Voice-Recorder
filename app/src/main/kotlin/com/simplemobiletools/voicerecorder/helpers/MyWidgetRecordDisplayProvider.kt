package com.simplemobiletools.voicerecorder.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.activities.MainActivity
import com.simplemobiletools.voicerecorder.extensions.drawableToBitmap

class MyWidgetRecordDisplayProvider : AppWidgetProvider() {
    private val OPEN_APP_INTENT_ID = 1

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
            RemoteViews(context.packageName, R.layout.widget_record_display).apply {
                setupAppOpenIntent(context, this)

                val selectedColor = context.config.widgetBgColor
                val alpha = Color.alpha(selectedColor)

                val bmp = getColoredIcon(context, selectedColor, alpha)
                setImageViewBitmap(R.id.record_display_btn, bmp)

                appWidgetManager.updateAppWidget(it, this)
            }
        }
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetRecordDisplayProvider::class.java)

    private fun setupAppOpenIntent(context: Context, views: RemoteViews) {
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.RECORD_INTENT_ACTION;
            val pendingIntent = PendingIntent.getActivity(context, OPEN_APP_INTENT_ID, this, PendingIntent.FLAG_CANCEL_CURRENT)
            views.setOnClickPendingIntent(R.id.record_display_btn, pendingIntent)
        }
    }

    private fun getColoredIcon(context: Context, color: Int, alpha: Int): Bitmap {
        val drawable = context.resources.getColoredDrawableWithColor(R.drawable.ic_microphone_vector, color, alpha)
        return context.drawableToBitmap(drawable)
    }
}
