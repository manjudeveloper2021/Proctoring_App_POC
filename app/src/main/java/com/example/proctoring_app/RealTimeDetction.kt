package com.example.proctoring_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.proctoring_app.databinding.ActivityRealTimeDetctionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// Import the necessary classes

private const val TAG = "FaceComparison"

class RealTimeDetction : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback  , VoiceDetectionListener , View.OnClickListener{
    private val binding by lazy { ActivityRealTimeDetctionBinding.inflate(layoutInflater) }

    var context: Context = this
    companion object {
        private val CAMERA_PERMISSION_REQUEST_CODE = 100
        const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var camera: Camera
    //Noise Detection
    private val noiseDetector by lazy { NoiseDetector() }
    var isRunning : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (checkPermission()) {
            startNoiseDetection()
        } else {
            requestPermission()
        }

        surfaceHolder = binding.surfaceView.holder
        surfaceHolder.addCallback(this)

        setOnClickListener()


    }

    private fun setOnClickListener() {
        binding.tvNoiseStatus.setOnClickListener(this)
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this,RECORD_AUDIO_PERMISSION)
        return result == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SoundActivity.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNoiseDetection()
            } else {
                // Handle permission denied
                requestPermission()
            }
        }
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
                noiseDetector.start(it,this@RealTimeDetction)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }else{
            surfaceHolder = holder
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
//            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            camera.setPreviewDisplay(holder)
            camera.setDisplayOrientation(90)
            camera.setPreviewCallback(this)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        camera.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera.stopPreview()
        camera.release()
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera) {
        // Fixing the NullPointerException
        if (data == null) {
            return
        }

        // Convert the data to a bitmap
        val parameters = camera.parameters
        val width = parameters.previewSize.width
        val height = parameters.previewSize.height
        val yuvImage = YuvImage(data, parameters.previewFormat, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        val lastUpdatedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        binding.progressHorizontal.isVisible = true

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()

        val detector = FaceDetection.getClient(options)


        val options1 = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptionsBase.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()

        // Create an object detector using the options
        val objectDetector = ObjectDetection.getClient(options1)


        if (data!=null){
//            90 for Camera back
//            270 for Camera front
            val frame = InputImage.fromByteArray(
                data,
                camera.parameters.previewSize.width,
                camera.parameters.previewSize.height,
                270,
                InputImage.IMAGE_FORMAT_NV21
            )
            binding.ivStatus.isVisible = false

            CoroutineScope(Dispatchers.Default).launch {
                detector.process(frame)
                    .addOnSuccessListener { faces ->

                        if (faces.size==0){
                            binding.textViewFaceCount.text = "Face size " + faces.size
                            binding.textViewFaceCount.setBackgroundColor(Color.RED)
                            binding.progressHorizontal.isVisible = false

                           // binding.ivStatus.setImageResource(R.drawable.outline_cancel_24)
                            binding.ivStatus.isVisible = false
                            binding.tvEyeStatus.text=""

                            return@addOnSuccessListener
                        }

                        // Process the detected faces
                        for (face in faces) {
                            // Access face landmarks, bounding box, etc.
                            runOnUiThread {
                                if (faces.size == 1) {
                                    binding.textViewFaceCount.text = "Face size " + faces.size
                                    binding.textViewFaceCount.setBackgroundColor(Color.GREEN)
                                    binding.progressHorizontal.isVisible = false

                                  //  binding.ivStatus.setImageResource(R.drawable.baseline_done_24)
                                    binding.ivStatus.isVisible = true

                                    binding.tvEyeStatus.text = eyeTrack(face)
                                    if (eyeTrack(face) == "both eyes are open"){
                                        binding.tvEyeStatus.setBackgroundColor(Color.GREEN)
                                    }else{
                                        binding.tvEyeStatus.setBackgroundColor(0)
                                    }

                                    if ( lipTrack(face)) {
                                        // Perform actions when the mouth is open
                                        println("Mouth is open")
                                        binding.ivStatus.setBackgroundResource(R.drawable.baseline_tag_faces_open)
                                    } else {
                                        // Perform actions when the mouth is closed
                                        println("Mouth is closed")
                                        binding.ivStatus.setBackgroundResource(R.drawable.baseline_face_close)
                                    }

                                }
                                else {
                                    binding.textViewFaceCount.text = "Face size " + faces.size
                                    binding.textViewFaceCount.setBackgroundColor(Color.RED)
                                    binding.progressHorizontal.isVisible = false
                                   // binding.ivStatus.setImageResource(R.drawable.outline_cancel_24)
                                    binding.ivStatus.isVisible = false

                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        // Handle any errors
                        runOnUiThread {
                            binding.textViewFaceCount.text = e.message

                        }
                    }
                /*OBJECT DETECTION*/
                objectDetector.process(frame)
                    .addOnSuccessListener { detectedObjects ->
                        // Process the detected objects
                        // Print all object names
                        for (detectedObject in detectedObjects) {
                            runOnUiThread {
                                val labels = detectedObject.labels
                                for (label in labels) {
                                    binding.textViewObject.text = label.text
//                            println(label.text)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Handle any errors that occur during object detection
                        runOnUiThread {
                            binding.textViewObject.text = exception.message
                        }
                    }
            }

        }


    }

    private fun eyeTrack(face: Face) :String {
        val leftEyeOpenProbability = face.leftEyeOpenProbability
        val rightEyeOpenProbability = face.rightEyeOpenProbability

        // Perform further actions based on eye status
        if (leftEyeOpenProbability != null) {
            if (rightEyeOpenProbability != null) {
                if (leftEyeOpenProbability >= 0.5 && rightEyeOpenProbability >= 0.5) {
                    // Both eyes are open
                    // Perform desired actions
                    return "both eyes are open"
                } else if (leftEyeOpenProbability < 0.5 && rightEyeOpenProbability >= 0.5) {
                    // Left eye is closed, right eye is open
                    // Perform desired actions
                    return "right eye is open"
                } else if (leftEyeOpenProbability >= 0.5 && rightEyeOpenProbability < 0.5) {
                    // Left eye is open, right eye is closed
                    // Perform desired actions
                    return "left eye is open"
                } else {
                    // Both eyes are closed
                    // Perform desired actions
                    return "both eyes are closed"
                }
            }
        }

        return "eye status not available"
    }

    private fun lipTrack(face: Face) :Boolean {
        //lips tracking
        val upperLipContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipContour = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points

        // Detect if the mouth is open or closed
        val thresholdValue = 0.5 // Set your desired threshold value here

        val upperLipHeight = upperLipContour?.maxByOrNull { it.y }?.y ?: 0f
        val lowerLipHeight = lowerLipContour?.minByOrNull { it.y }?.y ?: 0f

        val mouthOpen = (lowerLipHeight - upperLipHeight) > thresholdValue

        return mouthOpen
    }



    private fun showToast(msg: String) {
        runOnUiThread {
            binding.tvEyeStatus.text = msg
        }
    }


    private fun matchFace(bitmap1:Bitmap,bitmap2:Bitmap){
        // Create a FaceDetectorOptions object with the desired settings
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        // Create a FaceDetector instance
        val faceDetector = FaceDetection.getClient(options)
        // Create two InputImage objects from the provided images
        val image1 = InputImage.fromBitmap(bitmap1, 90)
        val image2 = InputImage.fromBitmap(bitmap2, 90)

        // Process the first image and get the faces detected
        val result1 = faceDetector.process(image1)
            .addOnSuccessListener { faces ->
                // Process the second image and get the faces detected
                val result2 = faceDetector.process(image2)
                    .addOnSuccessListener { faces2 ->
                        // Compare the faces detected in both images
                        val areFacesEqual = compareFaces(faces, faces2)
                        // Print the result
                        if (areFacesEqual) {
                            runOnUiThread {
                                Toast.makeText(this, "The faces are equal $areFacesEqual",Toast.LENGTH_SHORT).show()
                            }
                            println("The faces are equal.")
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "The faces are not equal $areFacesEqual",Toast.LENGTH_SHORT).show()
                            }
//                            println("The faces are not equal.")
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Handle any errors that occur during face detection
                        println("Face detection failed: ${exception.message}")
                    }
            }
            .addOnFailureListener { exception ->
                // Handle any errors that occur during face detection
                println("Face detection failed: ${exception.message}")
            }
    }

    private fun compareFaces(faces1: List<Face>, faces2: List<Face>): Boolean {
        if (faces1.size != faces2.size) {
            return false
        }

        for (i in faces1.indices) {
            val face1 = faces1[i]
            val face2 = faces2[i]

            // Compare bounding boxes
            if (face1.boundingBox != face2.boundingBox) {
                return false
            }

            // Compare landmarks
            if (face1.allLandmarks != face2.allLandmarks) {
                return false
            }
        }

        return true
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()

    }

    override fun onResume() {
        super.onResume()

    }
    private fun releaseCamera() {
        // check if Camera instance exists
        if (camera != null) {
            // first stop preview
            camera.stopPreview()
            // then cancel its preview callback
            camera.setPreviewCallback(null)
            camera.lock()
            // and finally release it
            camera.release()
            // sanitize you Camera object holder
        }
    }

    override fun onVoiceDetected(amplitude: Double, isNiceDetected: Boolean, isRunning: Boolean) {
        this@RealTimeDetction.isRunning = isRunning
        runOnUiThread {
            if (isRunning){
                if (isNiceDetected){
                    binding.tvNoiseStatus.text = "running "+amplitude
                    binding.tvNoiseStatus.setBackgroundColor(Color.RED)
                }else{
                    binding.tvNoiseStatus.text = "running "+amplitude
                    binding.tvNoiseStatus.setBackgroundColor(Color.GREEN)
                }
            }else{
                binding.tvNoiseStatus.text = "Stoped -> Resume now"
                binding.tvNoiseStatus.setBackgroundColor(Color.CYAN)
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseDetector.stop()
    }

    override fun onClick(view: View?) {
        when(view?.id){
                R.id.tvNoiseStatus -> {
                    if (!isRunning) startNoiseDetection() else noiseDetector.stop()
                }
        }
    }
}