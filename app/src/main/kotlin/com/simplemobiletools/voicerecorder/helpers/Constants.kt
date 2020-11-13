package com.simplemobiletools.voicerecorder.helpers

import android.annotation.SuppressLint
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import com.simplemobiletools.commons.helpers.isQPlus

const val RECORDER_RUNNING_NOTIF_ID = 10000

private const val PATH = "com.simplemobiletools.voicerecorder.action."
const val GET_RECORDER_INFO = PATH + "GET_RECORDER_INFO"
const val STOP_AMPLITUDE_UPDATE = PATH + "STOP_AMPLITUDE_UPDATE"
const val TOGGLE_PAUSE = PATH + "TOGGLE_PAUSE"

const val EXTENSION_M4A = 0
const val EXTENSION_MP3 = 1

const val RECORDING_RUNNING = 0
const val RECORDING_STOPPED = 1
const val RECORDING_PAUSED = 2

// shared preferences
const val HIDE_NOTIFICATION = "hide_notification"
const val SAVE_RECORDINGS = "save_recordings"
const val EXTENSION = "extension"

@SuppressLint("InlinedApi")
fun getAudioFileContentUri(id: Long): Uri {
    val baseUri = if (isQPlus()) {
        Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        Media.EXTERNAL_CONTENT_URI
    }

    return ContentUris.withAppendedId(baseUri, id)
}
