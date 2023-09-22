package com.example.proctoring_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.proctoring_app.databinding.ActivitySoundBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SoundActivity : AppCompatActivity(), VoiceDetectionListener ,View.OnClickListener  {
    private val binding by lazy { ActivitySoundBinding.inflate(layoutInflater) }
    private val noiseDetector by lazy { NoiseDetector() }
    private val context by lazy { this }
    var isRunning : Boolean = false


    companion object {
        const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_REQUEST_CODE = 1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        if (checkPermission()) {
            startNoiseDetection()
        } else {
            requestPermission()
        }

        init1()

    }


    private fun init1() {
        binding.textview.setOnClickListener(this)
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, RECORD_AUDIO_PERMISSION)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(RECORD_AUDIO_PERMISSION),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startNoiseDetection() {
        // always run in background thread
        CoroutineScope(Dispatchers.Default).launch {
            context.let {
                noiseDetector.start(it,this@SoundActivity)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNoiseDetection()
            } else {
                // Handle permission denied
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        noiseDetector.stop()
    }
    @SuppressLint("SetTextI18n")
    override fun onVoiceDetected(amplitude: Double, isNiceDetected: Boolean, isRunning: Boolean) {
        this@SoundActivity.isRunning = isRunning
        runOnUiThread {
            if (isRunning){
                if (isNiceDetected){
                    binding.textview.text = "running "+amplitude
                    binding.textview.setBackgroundColor(Color.RED)
                }else{
                    binding.textview.text = "running "+amplitude
                    binding.textview.setBackgroundColor(Color.GREEN)
                }
            }else{
                binding.textview.text = "Stoped -> Resume now"
                binding.textview.setBackgroundColor(Color.CYAN)
            }

        }
    }

    /*override fun onVoiceToText(get: String?) {

    }*/
    override fun onClick(v: View?) {
        when(v?.id){
            R.id.textview -> {
                if (!isRunning) startNoiseDetection() else noiseDetector.stop()
            }
        }

    }
}