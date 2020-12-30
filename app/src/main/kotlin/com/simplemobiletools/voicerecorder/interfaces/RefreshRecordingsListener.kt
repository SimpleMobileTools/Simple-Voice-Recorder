package com.simplemobiletools.voicerecorder.interfaces

import com.simplemobiletools.voicerecorder.models.Recording

interface RefreshRecordingsListener {
    fun refreshRecordings()

    fun playRecording(recording: Recording, playOnPrepared: Boolean)
}
