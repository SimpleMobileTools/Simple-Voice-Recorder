package com.simplemobiletools.voicerecorder.models

class Events {
    class RecordingDuration internal constructor(val duration: Int)
    class RecordingStatus internal constructor(val status: Int)
    class RecordingAmplitude internal constructor(val amplitude: Int)
    class RecordingCompleted internal constructor()
}
