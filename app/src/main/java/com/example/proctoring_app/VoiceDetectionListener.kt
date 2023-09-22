package com.example.proctoring_app

interface VoiceDetectionListener {
    fun onVoiceDetected(amplitude: Double, isNiceDetected: Boolean, isRunning: Boolean)
   /* fun onVoiceToText(get: String?)*/
}