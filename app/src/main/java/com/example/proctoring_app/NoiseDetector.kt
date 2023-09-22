package com.example.proctoring_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat

class NoiseDetector {
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    fun start(context: Context , voiceDetectionListener : VoiceDetectionListener) {

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        audioRecord?.startRecording()
        isRunning = true

        val buffer = ShortArray(BUFFER_SIZE)

        while (isRunning) {
            val readSize = audioRecord?.read(buffer, 0, BUFFER_SIZE)
            if (readSize != AudioRecord.ERROR_INVALID_OPERATION) {
                val amplitude = calculateAmplitude(buffer, readSize!!)
                Log.e("TAG", "start: $amplitude")
                if (amplitude > 500) {
                    voiceDetectionListener.onVoiceDetected(amplitude,true,isRunning)
                }else{
                    voiceDetectionListener.onVoiceDetected(amplitude,false,isRunning)
                }
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun stop() {
        isRunning = false
    }

    private fun calculateAmplitude(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        val amplitude = Math.sqrt(sum / readSize)
        return amplitude
    }
}

