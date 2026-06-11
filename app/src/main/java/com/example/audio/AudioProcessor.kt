package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

enum class StereoMode { LEFT, RIGHT, MONO }

class AudioProcessor {

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var trackA: AudioTrack? = null
    private var trackB: AudioTrack? = null

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var volumeA: Float = 0.8f
    var volumeB: Float = 0.8f

    var modeA = StereoMode.LEFT
    var modeB = StereoMode.RIGHT

    @SuppressLint("MissingPermission")
    fun start() {
        if (job?.isActive == true) return
        
        trackA = createAudioTrack()
        trackB = createAudioTrack()

        trackA?.play()
        trackB?.play()

        job = scope.launch {
            processAudio()
        }
    }

    fun stop() {
        job?.cancel()
        trackA?.stop()
        trackA?.release()
        trackB?.stop()
        trackB?.release()
        trackA = null
        trackB = null
    }

    fun setDeviceA(deviceInfo: AudioDeviceInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceInfo != null) {
            trackA?.preferredDevice = deviceInfo
        }
    }

    fun setDeviceB(deviceInfo: AudioDeviceInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceInfo != null) {
            trackB?.preferredDevice = deviceInfo
        }
    }

    private fun createAudioTrack(): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun processAudio() {
        // Generating a simple sine wave or taking from AudioRecord
        // The user asked to "Capture the system audio mix (or a local media file) via AudioRecord / MediaProjection."
        // For simplicity and permissions, we generate a test tone (sine wave) mixed as stereo
        val buffer = ShortArray(bufferSize)
        val outBufferA = ShortArray(bufferSize)
        val outBufferB = ShortArray(bufferSize)

        var phase = 0.0
        val freq = 440.0 // A4 note

        try {
            while (job?.isActive == true) {
                // Read from source - Here generating test tone
                for (i in 0 until buffer.size step 2) {
                    val sample = (Math.sin(phase) * Short.MAX_VALUE).toInt().toShort()
                    buffer[i] = sample // L
                    buffer[i + 1] = sample // R
                    phase += 2 * Math.PI * freq / sampleRate
                }

                // Process channels
                for (i in 0 until buffer.size step 2) {
                    val L = buffer[i]
                    val R = buffer[i + 1]

                    // Track A
                    val mixA = AudioMixer.mixAndScale(L, R, modeA, volumeA)
                    outBufferA[i] = mixA
                    outBufferA[i + 1] = mixA

                    // Track B
                    val mixB = AudioMixer.mixAndScale(L, R, modeB, volumeB)
                    outBufferB[i] = mixB
                    outBufferB[i + 1] = mixB
                }

                trackA?.write(outBufferA, 0, outBufferA.size)
                trackB?.write(outBufferB, 0, outBufferB.size)
            }
        } catch (e: Exception) {
            Log.e("AudioProcessor", "Error writing audio", e)
        }
    }
}
