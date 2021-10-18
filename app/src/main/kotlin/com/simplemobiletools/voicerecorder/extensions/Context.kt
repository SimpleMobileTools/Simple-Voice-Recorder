package com.simplemobiletools.voicerecorder.extensions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.simplemobiletools.voicerecorder.helpers.Config
import com.simplemobiletools.voicerecorder.helpers.IS_RECORDING
import com.simplemobiletools.voicerecorder.helpers.MyWidgetRecordDisplayProvider
import com.simplemobiletools.voicerecorder.helpers.TOGGLE_WIDGET_UI

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.drawableToBitmap(drawable: Drawable): Bitmap {
    val size = (60 * resources.displayMetrics.density).toInt()
    val mutableBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mutableBitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return mutableBitmap
}

fun Context.updateWidgets(isRecording: Boolean) {
    val widgetIDs = AppWidgetManager.getInstance(applicationContext)
        ?.getAppWidgetIds(ComponentName(applicationContext, MyWidgetRecordDisplayProvider::class.java)) ?: return
    if (widgetIDs.isNotEmpty()) {
        Intent(applicationContext, MyWidgetRecordDisplayProvider::class.java).apply {
            action = TOGGLE_WIDGET_UI
            putExtra(IS_RECORDING, isRecording)
            sendBroadcast(this)
        }
    }
}
