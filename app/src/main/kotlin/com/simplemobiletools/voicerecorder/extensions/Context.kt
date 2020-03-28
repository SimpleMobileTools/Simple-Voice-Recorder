package com.simplemobiletools.voicerecorder.extensions

import android.content.Context
import com.simplemobiletools.voicerecorder.helpers.Config

val Context.config: Config get() = Config.newInstance(applicationContext)
