package com.simplemobiletools.voicerecorder.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.recorder.Recorder
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs


class Mp3Recorder(val context: Context) : Recorder {
    val TAG = "Mp3Helper"

    private var androidLame: AndroidLame? = null
    private var mp3buffer: ByteArray = ByteArray(0)
    private var isPaused = AtomicBoolean(false)
    private var isStopped = AtomicBoolean(false)
    private var outputPath: String? = null
    private var fileDescriptor: FileDescriptor? = null
    private var amplitude = AtomicInteger(0)
    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.CAMCORDER,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBufferSize * 2
    )

    override fun setOutputFile(path: String) {
        outputPath = path
    }

    override fun prepare() {}

    override fun start() {
        val data = ShortArray(minBufferSize)
        mp3buffer = ByteArray((7200 + data.size * 2 * 1.25).toInt())

        val outputStream: FileOutputStream = try {
            if (fileDescriptor != null) {
                FileOutputStream(fileDescriptor)
            } else {
                FileOutputStream(File(outputPath))
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        }

        val androidLame = LameBuilder()
            .setInSampleRate(SAMPLE_RATE)
            .setOutBitrate(context.config.bitrate / 1000)
            .setOutSampleRate(SAMPLE_RATE)
            .setOutChannels(1)
            .build()

        ensureBackgroundThread {
            audioRecord.startRecording()

            while (!isStopped.get()) {
                if (!isPaused.get()) {
                    val count = audioRecord.read(data, 0, minBufferSize)
                    if (count > 0) {
                        val bytesEncoded: Int = androidLame.encode(data, data, count, mp3buffer)
                        if (bytesEncoded > 0) {
                            try {
                                updateAmplitude(data)
                                outputStream.write(mp3buffer, 0, bytesEncoded)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        isPaused.set(true)
        isStopped.set(true)
    }

    override fun pause() {
        isPaused.set(true)
    }

    override fun resume() {
        isPaused.set(false)
    }

    override fun release() {
        androidLame?.flush(mp3buffer)
    }

    override fun getMaxAmplitude(): Int {
        return amplitude.get()
    }

    override fun setOutputFile(fileDescriptor: FileDescriptor) {
        this.fileDescriptor = fileDescriptor
    }

    private fun updateAmplitude(data: ShortArray) {
        var sum = 0L
        for (i in 0 until minBufferSize step 2) {
            sum += abs(data[i].toInt())
        }
        amplitude.set((sum / (minBufferSize / 8)).toInt())
    }
}
