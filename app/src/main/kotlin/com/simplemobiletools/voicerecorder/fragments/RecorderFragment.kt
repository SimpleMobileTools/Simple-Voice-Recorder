package com.simplemobiletools.voicerecorder.fragments

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isNougatPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.helpers.*
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.services.RecorderService
import kotlinx.android.synthetic.main.fragment_recorder.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class RecorderFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var status = RECORDING_STOPPED
    private var pauseBlinkTimer = Timer()
    private var bus: EventBus? = null

    override fun onResume() {
        setupColors()
        if (!RecorderService.isRunning) {
            status = RECORDING_STOPPED
        }

        refreshView()
    }

    override fun onDestroy() {
        bus?.unregister(this)
        pauseBlinkTimer.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupColors()
        recorder_visualizer.recreate()
        bus = EventBus.getDefault()
        bus!!.register(this)

        updateRecordingDuration(0)
        toggle_recording_button.setOnClickListener {
            (context as? BaseSimpleActivity)?.handleNotificationPermission { granted ->
                if (granted) {
                    toggleRecording()
                } else {
                    PermissionRequiredDialog(
                        context as? BaseSimpleActivity ?: return@handleNotificationPermission,
                        messageId = com.simplemobiletools.commons.R.string.no_post_notifications_permissions,
                    ) {
                        context.openNotificationSettings()
                    }
                }
            }
        }

        toggle_pause_button.setOnClickListener {
            Intent(context, RecorderService::class.java).apply {
                action = TOGGLE_PAUSE
                context.startService(this)
            }
        }

        Intent(context, RecorderService::class.java).apply {
            action = GET_RECORDER_INFO
            try {
                context.startService(this)
            } catch (e: Exception) {
            }
        }
    }

    private fun setupColors() {
        val properPrimaryColor = context.getProperPrimaryColor()
        toggle_recording_button.apply {
            setImageDrawable(getToggleButtonIcon())
            background.applyColorFilter(properPrimaryColor)
        }

        toggle_pause_button.apply {
            setImageDrawable(resources.getColoredDrawableWithColor(R.drawable.ic_pause_vector, properPrimaryColor.getContrastColor()))
            background.applyColorFilter(properPrimaryColor)
        }

        recorder_visualizer.chunkColor = properPrimaryColor
        recording_duration.setTextColor(context.getProperTextColor())
    }

    private fun updateRecordingDuration(duration: Int) {
        recording_duration.text = duration.getFormattedDuration()
    }

    private fun getToggleButtonIcon(): Drawable {
        val drawable = if (status == RECORDING_RUNNING || status == RECORDING_PAUSED) R.drawable.ic_stop_vector else R.drawable.ic_microphone_vector
        return resources.getColoredDrawableWithColor(drawable, context.getProperPrimaryColor().getContrastColor())
    }

    private fun toggleRecording() {
        status = if (status == RECORDING_RUNNING || status == RECORDING_PAUSED) {
            RECORDING_STOPPED
        } else {
            RECORDING_RUNNING
        }

        toggle_recording_button.setImageDrawable(getToggleButtonIcon())

        if (status == RECORDING_RUNNING) {
            startRecording()
        } else {
            toggle_pause_button.beGone()
            stopRecording()
        }
    }

    private fun startRecording() {
        Intent(context, RecorderService::class.java).apply {
            context.startService(this)
        }
        recorder_visualizer.recreate()
    }

    private fun stopRecording() {
        Intent(context, RecorderService::class.java).apply {
            context.stopService(this)
        }
    }

    private fun getPauseBlinkTask() = object : TimerTask() {
        override fun run() {
            if (status == RECORDING_PAUSED) {
                // update just the alpha so that it will always be clickable
                Handler(Looper.getMainLooper()).post {
                    toggle_pause_button.alpha = if (toggle_pause_button.alpha == 0f) 1f else 0f
                }
            }
        }
    }

    private fun refreshView() {
        toggle_recording_button.setImageDrawable(getToggleButtonIcon())
        toggle_pause_button.beVisibleIf(status != RECORDING_STOPPED && isNougatPlus())
        pauseBlinkTimer.cancel()

        if (status == RECORDING_PAUSED) {
            pauseBlinkTimer = Timer()
            pauseBlinkTimer.scheduleAtFixedRate(getPauseBlinkTask(), 500, 500)
        }

        if (status == RECORDING_RUNNING) {
            toggle_pause_button.alpha = 1f
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotDurationEvent(event: Events.RecordingDuration) {
        updateRecordingDuration(event.duration)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotStatusEvent(event: Events.RecordingStatus) {
        status = event.status
        refreshView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotAmplitudeEvent(event: Events.RecordingAmplitude) {
        val amplitude = event.amplitude
        if (status == RECORDING_RUNNING) {
            recorder_visualizer.update(amplitude)
        }
    }
}
