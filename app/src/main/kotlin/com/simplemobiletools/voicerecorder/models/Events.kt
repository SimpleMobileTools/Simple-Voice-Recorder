package com.simplemobiletools.voicerecorder.models

class Events {
    class RecordingDuration internal constructor(val duration: Int)
    class RecordingStatus internal constructor(val isRecording: Boolean)
    class RecordingAmplitude internal constructor(val amplitude: Int)
}
