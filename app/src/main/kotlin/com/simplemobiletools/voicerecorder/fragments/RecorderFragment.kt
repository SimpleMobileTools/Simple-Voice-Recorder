package com.simplemobiletools.voicerecorder.fragments

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isNougatPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
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
        if (!RecorderService.isRunning) status = RECORDING_STOPPED
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
            toggleRecording()
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
        val adjustedPrimaryColor = context.getAdjustedPrimaryColor()
        toggle_recording_button.apply {
            setImageDrawable(getToggleButtonIcon())
            background.applyColorFilter(adjustedPrimaryColor)
        }

        toggle_pause_button.apply {
            setImageDrawable(resources.getColoredDrawableWithColor(R.drawable.ic_pause_vector, adjustedPrimaryColor.getContrastColor()))
            background.applyColorFilter(adjustedPrimaryColor)
        }

        recorder_visualizer.chunkColor = adjustedPrimaryColor
        recording_duration.setTextColor(context.config.textColor)
    }

    private fun updateRecordingDuration(duration: Int) {
        recording_duration.text = duration.getFormattedDuration()
    }

    private fun getToggleButtonIcon(): Drawable {
        val drawable = if (status == RECORDING_RUNNING || status == RECORDING_PAUSED) R.drawable.ic_stop_vector else R.drawable.ic_microphone_vector
        return resources.getColoredDrawableWithColor(drawable, context.getAdjustedPrimaryColor().getContrastColor())
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
        if (status == RECORDING_PAUSED) {
            pauseBlinkTimer = Timer()
            pauseBlinkTimer.scheduleAtFixedRate(getPauseBlinkTask(), 500, 500)
        } else {
            pauseBlinkTimer.cancel()
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
