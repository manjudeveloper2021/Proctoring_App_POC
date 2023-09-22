package com.example.proctoring_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Rect
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraCapture : AppCompatActivity(), VoiceDetectionListener{
    private var usbReceiver: UsbReceiver? = null

    private var statusBarLocker: StatusBarLocker? = null
    private val SPEECH_REQUEST_CODE = 0
    var photoFile2: File? = null
    val CAPTURE_IMAGE_REQUEST = 200
    var mCurrentPhotoPath: String? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var files: Array<File>
    private lateinit var filesPaths: Array<String>
    private lateinit var filesNames: Array<String>
    private var lipMovementDetector: LipDetector? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private val noiseDetector by lazy { NoiseDetector() }
    var isRunning : Boolean = false
    var isReadableModeEnabled = false
    private  val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    private  val PERMISSION_REQUEST_CODE = 1
    override fun onBackPressed() {
        //on back pressed
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeTitleBar()
        if (actionBar != null) actionBar!!.hide()
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        doNotLockScreen()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
       window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                actionBar?.hide()
            }
        }
        statusBarLocker = StatusBarLocker(this)
        statusBarLocker!!.lock()
        setContentView(R.layout.acivity_cameraviewcapture)




        if (checkPermission()) {
            startNoiseDetection()
        } else {
            requestPermission()
        }

        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        if (!deviceList.isEmpty()) {
            for (device in deviceList.values) {
                val deviceName = device.deviceName
                val productId = device.productId
                val vendorId = device.vendorId
               Toast.makeText(this, "USB Connected", Toast.LENGTH_LONG).show()
            }
        } else {
        Toast.makeText(this, "USB Disconnected", Toast.LENGTH_LONG).show()
        }
        supportActionBar?.hide()
        val pm = packageManager
        val micPresent = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        var notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && !notificationManager.isNotificationPolicyAccessGranted
        ) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
       lipMovementDetector = LipDetector()
       OpenCVLoader.initDebug()
        var btn_submit = findViewById<Button>(R.id.button_capture)
        var btn_stop = findViewById<Button>(R.id.button_stop)
        var btn_view = findViewById<Button>(R.id.button_view)
        var btn_back = findViewById<Button>(R.id.button_back)
        var layout_view = findViewById<LinearLayout>(R.id.layout_btn)
        var grid_view = findViewById<GridView>(R.id.gridview)
      turnOffDeveloperMode(this)
        val activity: Activity = this
        enableFullScreen(activity)
        hideStatusBar(activity)
        isSlowNetwork(activity)
        onMultiWindowModeChanged(false)
        val connectivityManager =
            this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val downSpeed = nc!!.linkDownstreamBandwidthKbps/1000
        val upSpeed = nc.linkUpstreamBandwidthKbps/1000
        Toast.makeText(this, "Up Speed: $upSpeed Mbps \nDown Speed: $downSpeed Mbps", Toast.LENGTH_LONG).show()
        if (isEmulatorRun()) {
            btn_submit.visibility =View.GONE
            btn_stop.visibility =View.GONE
            Toast.makeText(this, "You can't run your app on any emulator", Toast.LENGTH_LONG).show()
        }
        else {
            btn_submit.visibility =View.VISIBLE
            btn_stop.visibility =View.VISIBLE
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }

            outputDirectory = getOutputDirectory()
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        btn_submit.setOnClickListener {
            Toast.makeText(activity, "Your session is start",Toast.LENGTH_LONG).show()
                takePhoto()
        }
        btn_stop.setOnClickListener {
            Toast.makeText(activity, "Your session is stop",Toast.LENGTH_LONG).show()
            layout_view.visibility = View.GONE
            btn_view.visibility = View.VISIBLE
        }
        btn_view.setOnClickListener {
            val dirDownload =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (dirDownload != null) {
                if (dirDownload.isDirectory) {
                    files = dirDownload.listFiles()
                    if(files.size != 0){
                        filesPaths =  Array(files.size) { "" }
                        filesNames =  Array(files.size) { "" }
                        for (i in 0 until files.size) {
                            filesPaths[i] = files[i].absolutePath
                            filesNames[i] = files[i].name
                        }
                    }

                }
            }
            btn_back.visibility = View.VISIBLE
            btn_view.visibility = View.GONE
            grid_view.visibility = View.VISIBLE
            grid_view.adapter = ImageAdapter(this, filesNames, filesPaths)
        }
        btn_back.setOnClickListener {
            btn_submit.visibility = View.VISIBLE
            btn_stop.visibility = View.VISIBLE
            btn_view.visibility = View.GONE
            btn_back.visibility = View.GONE
            grid_view.visibility = View.GONE
        }

        val folderName = "Images" // Replace with your desired folder name
        val picturesDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val newFolder = File(picturesDirectory, folderName)
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "."+folderName
        )
    }
    fun getFileUri(fileName: String): Uri? {
        var imageUri: Uri? = null
        val typestr = "/images/"
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ).path, typestr + fileName
        )
        imageUri = Uri.fromFile(mediaStorageDir)
        return imageUri
    }
    // Create an intent that can start the Speech Recognizer activity
    private fun displaySpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        // This starts the activity and populates the intent with the speech text.
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }
    // This callback is invoked when the Speech Recognizer returns.
// This is where you process the intent and extract the speech text from the intent.
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "IMG2_" + timeStamp + "_"
        val file_uri = getFileUri(imageFileName)
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        val image = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )
        mCurrentPhotoPath = image.absolutePath

        return image
    }
    private fun displayMessage(context: Context, message: String) {
       Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAPTURE_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val myBitmap = BitmapFactory.decodeFile(photoFile2!!.absolutePath)
        }
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results: List<String>? = data!!.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            val spokenText = results!![0]
            // Do something with spokenText.
        }
        else {
            displayMessage(baseContext, "Request cancelled or something went wrong.")
        }
    }
    private fun doNotLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
    private fun removeTitleBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        photoFile2 = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile2!!).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                   if(photoFile2 == null){

                   }
                    else{
                       val savedUri = Uri.fromFile(photoFile2)
                       findViewById<ImageView>(R.id.imageView).visibility = View.VISIBLE
                       findViewById<ImageView>(R.id.imageView).setImageURI(savedUri)
                       val msg = "Photo capture succeeded: $savedUri"
                       //   Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                       Log.d(TAG, msg)
                   }



                }
            })
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        }
        else {
            val takePictureIntent = null //Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                photoFile2 = createImageFile()
                // Continue only if the File was successfully created
                if (photoFile2 != null) {
                  //  displayMessage(baseContext, "Saved")
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "com.example.proctoring_app.fileprovider",
                        photoFile2!!

                    )
                    //takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    //  takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                    takePictureIntent?.let { startActivityForResult(it, CAPTURE_IMAGE_REQUEST) }
                }
            } catch (ex: Exception) {
              //  displayMessage(baseContext, ex.message.toString())
            }
        }
    }
    private fun startCamera() {
      //  var viewFinder = findViewById(R.id.viewFinder) as PreviewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).createSurfaceProvider())
                }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        if (requestCode == SoundActivity.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNoiseDetection()
            } else {
                // Handle permission denied
            }
        }
    }
    companion object {
        private const val TAG = "CameraXGFG"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onResume() {
        super.onResume()
        usbReceiver = UsbReceiver()
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
        hideSystemUI()
        toggleReadableMode()
   turnOffDeveloperMode(this)
            val handler = Handler()
            val delay = 20000 // 1000 milliseconds == 1 second
            handler.postDelayed(object : Runnable {
                override fun run() {
                    takePhoto() // Do your work here
                    handler.postDelayed(this, delay.toLong())
                }
            }, delay.toLong())

    }
    fun isDeveloperModeEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
    }
    fun turnOffDeveloperMode(context: Context) {
        if (isDeveloperModeEnabled(context)) {
            try {
                AlertDialog.Builder(this)
                    .setTitle("Please Disable Developer Mode")
                    .setMessage("You will not proceed if developer mode is enable")
                    .setPositiveButton("Go to Settings",
                        DialogInterface.OnClickListener { dialog, which -> startActivity(Intent(
                            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) })
                    .setIcon(R.drawable.ic_dialog_error)
                    .setCancelable(false)
                    .show()
                Settings.Secure.putInt(
                    context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
                )
            } catch (e: SecurityException) {
                // Handle the security exception if necessary
            }
        }
    }
    fun enableFullScreen(activity: Activity) {
        // Hide the status bar

        activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        // Hide the navigation bar (optional, depending on your use case)
        activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)


        // Set the window to full-screen mode
        val window: Window = activity.window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN

        )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

    }
    fun hideStatusBar(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }
    private fun isEmulatorRun(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.MANUFACTURER.contains("Google")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
                || Build.PRODUCT.contains("Genymotion")
                ||Build.PRODUCT.contains("Bluestacks"))
    }
    fun hideSystemUI() {
        if (supportActionBar != null) {
            supportActionBar!!.hide()
        }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }
    @SuppressLint("WrongConstant", "PrivateApi")
    fun setExpandNotificationDrawer(context: Context, expand: Boolean) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val methodName =
                if (expand)
                    if (Build.VERSION.SDK_INT >= 22) "expandNotificationsPanel" else "expand"

                else
                    if (Build.VERSION.SDK_INT >= 22) "collapsePanels" else "collapse"
            val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
            val method: Method = statusBarManager.getMethod(methodName)
            method.invoke(statusBarService)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    @SuppressLint("WrongConstant")
    override fun onWindowFocusChanged(hasFocus: Boolean) {

        if (hasFocus) { // hasFocus is true
          hideStatusBar(this)
            hideSystemUI()
            setExpandNotificationDrawer(this,false)
//            AlertDialog.Builder(this)
//                .setTitle("You can't access status bar while playing quiz..!!")
//                .setMessage("Do you want to exit")
//                .setCancelable(false)
//                .setPositiveButton("Exit",
//                    DialogInterface.OnClickListener { dialog, which -> finish() })
//                .setNegativeButton("Cancel",
//                    DialogInterface.OnClickListener { dialog, which ->  })
//                .setIcon(android.R.drawable.ic_dialog_info)
//                .setCancelable(false)
//                .show()
//          hideSystemUI()

//            Log.i("Tag", "Notification bar is pulled down");
           // setExpandNotificationDrawer(this, false)
        }

        else {
            // hasFocus is false
//            val service = getSystemService("statusbar")
//            val statusbarManager = Class.forName("android.app.StatusBarManager")
//            val collapse = statusbarManager.getMethod("collapse")
//            collapse.isAccessible = true
//            collapse.invoke(service)
            if (!hasFocus) {
                //Toast.makeText(this,"NOTIFICATION BAR IS DOWN",Toast.LENGTH_SHORT).show()
                // NOTIFICATION BAR IS DOWN...DO STUFF
          // close notification panel
                            AlertDialog.Builder(this)
                .setTitle("You can't access status bar while playing quiz..!!")
                .setMessage("Do you want to exit")
                .setCancelable(false)
                .setPositiveButton("Exit",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("Cancel",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(android.R.drawable.ic_dialog_dialer)
                .setCancelable(false)
                .show()
                setExpandNotificationDrawer(this,false)
            }

        }
        super.onWindowFocusChanged(hasFocus)

    }




    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK ){
            AlertDialog.Builder(this)
                .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
                .setMessage("Do you want to exit")
                .setPositiveButton("Yes",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("No",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(R.drawable.ic_dialog_error)
                .setCancelable(false)
                .show()
            // moveTaskToBack(false)
          //  Toast.makeText(this, "Home",  Toast.LENGTH_SHORT).show()
            // return true
        }
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            AlertDialog.Builder(this)
                .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
                .setMessage("Do you want to exit")
                .setPositiveButton("Yes",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("No",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(R.drawable.ic_dialog_error)
                .setCancelable(false)
                .show()
            // moveTaskToBack(false)
          //  Toast.makeText(this, "Home",  Toast.LENGTH_SHORT).show()
            // return true
        }
         if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
            AlertDialog.Builder(this)
                .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
                .setMessage("Do you want to exit")
                .setPositiveButton("Yes",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("No",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(R.drawable.ic_dialog_error)
                .setCancelable(false)
                .show()

            // moveTaskToBack(false)

            // return true
        }
        else{

        }
        return false
    }
 override fun onStop() {
    super.onStop()
    AlertDialog.Builder(this)
        .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
        .setMessage("Do you want to exit")
        .setPositiveButton("Yes",
            DialogInterface.OnClickListener { dialog, which -> finish() })
        .setNegativeButton("No",
            DialogInterface.OnClickListener { dialog, which ->  })
        .setIcon(R.drawable.ic_dialog_error)
        .setCancelable(false)
        .show()

    // insert here your instructions
}
    override fun onPause() {
        super.onPause()
        if (this.isFinishing){
            AlertDialog.Builder(this)
                .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
                .setMessage("Do you want to exit")
                .setPositiveButton("Yes",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("No",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(R.drawable.ic_dialog_error)
                .setCancelable(false)
                .show()

        }
        else{
            AlertDialog.Builder(this)
                .setTitle("If you want to close the app, then it will lose data and you have start this again ..!!")
                .setMessage("Do you want to exit")
                .setPositiveButton("Yes",
                    DialogInterface.OnClickListener { dialog, which -> finish() })
                .setNegativeButton("No",
                    DialogInterface.OnClickListener { dialog, which ->  })
                .setIcon(R.drawable.ic_dialog_error)
                .setCancelable(false)
                .show()
        }
        if (usbReceiver != null) {
            unregisterReceiver(usbReceiver);
            usbReceiver = null;
        }
    }
    override fun onDestroy() {

        statusBarLocker!!.release()
        noiseDetector.stop()
        super.onDestroy()
    }

    fun goToSleep(context: Context) {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        try {
            powerManager.javaClass.getMethod(
                "goToSleep",
                *arrayOf<Class<*>?>(Long::class.javaPrimitiveType)
            ).invoke(powerManager, SystemClock.uptimeMillis())
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }
    }
    private fun processFrame(frame: Mat) {
        val lipMovementDetected = lipMovementDetector!!.isLipMovementDetected(frame)
        val lips: Array<Rect> = lipMovementDetector!!.detectLips(frame)
        // Handle the result (e.g., trigger proctoring action if lip movement is detected)
        // ...
    }
    @RequiresApi(Build.VERSION_CODES.M)
    fun isSlowNetwork(context: Context): Boolean {
        var isSlow: Boolean = false
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (networkCapabilities != null) {
            // Check if the network has a good transport (like WIFI or Ethernet)
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            ) {
                // Check if the network is fast (e.g., 3G or 4G)
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) {
//                if (networkCapabilities.linkUpstreamBandwidthKbps > 400) {
                    if (networkCapabilities.linkUpstreamBandwidthKbps > 100) {
                        isSlow = false
                    } else {
                        Toast.makeText(context, "Slow internet connection", Toast.LENGTH_SHORT).show()
                        isSlow =  true
                    }
                } else {
                    Toast.makeText(context, "Slow internet connection", Toast.LENGTH_SHORT)
                        .show()
                    isSlow = true
                }
            } else {
                Toast.makeText(context, "No WIFI or No connection", Toast.LENGTH_SHORT)
                    .show()
                isSlow = true
            }
        }
        return isSlow
    }
    fun hideFolder(folderPath: String) {
        val folder = File(folderPath)
        if (folder.exists()) {
            val renamedFolder = File(folder.parent, ".${folder.name}")
            folder.renameTo(renamedFolder)
        } else {
            throw Exception("Folder does not exist")
        }
    }
    fun main() {
        val folderPath = "/storage/emulated/0/MyFolder"
        try {
            hideFolder(folderPath)
            println("Folder hidden successfully")
        } catch (e: Exception) {
            println("Failed to hide folder: ${e.message}")
        }
    }
    private fun init1() {
       // binding.textview.setOnClickListener(this)
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, SoundActivity.RECORD_AUDIO_PERMISSION)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(SoundActivity.RECORD_AUDIO_PERMISSION),
            SoundActivity.PERMISSION_REQUEST_CODE
        )
    }

    private fun startNoiseDetection() {
        // always run in background thread
        CoroutineScope(Dispatchers.Default).launch {
            this@CameraCapture.let {
                noiseDetector.start(it,this@CameraCapture)
            }
        }
    }
    @SuppressLint("SetTextI18n")
    override fun onVoiceDetected(amplitude: Double, isNoiseDetected: Boolean, isRunning: Boolean) {
        this@CameraCapture.isRunning = isRunning
        runOnUiThread {
            if (isRunning){
                if (isNoiseDetected){
                   // binding.textview.text = "running "+amplitude
                  //  binding.textview.setBackgroundColor(Color.RED)
                    AlertDialog.Builder(this)
                        .setTitle("Your voice is detected. You are not allowed to take quiz..!!")
                        .setMessage("Do you want to exit")
                        .setPositiveButton("Yes",
                            DialogInterface.OnClickListener { dialog, which -> finishAffinity() })
                        .setNegativeButton("No",
                            DialogInterface.OnClickListener { dialog, which ->  })
                        .setIcon(R.drawable.ic_dialog_error)
                        .setCancelable(false)
                        .show()
                }else{
                  //  binding.textview.text = "running "+amplitude
                  //  binding.textview.setBackgroundColor(Color.GREEN)
                }
            }else{
               // binding.textview.text = "Stoped -> Resume now"
               // binding.textview.setBackgroundColor(Color.CYAN)
            }

        }
    }
    fun toggleReadableMode() {
        isReadableModeEnabled = !isReadableModeEnabled

        // Apply readable mode changes
        applyReadableModeChanges()
    }
    // Function to apply readable mode changes
    fun applyReadableModeChanges() {
        val textColor =
            if (isReadableModeEnabled) resources.getColor(R.color.gray) else Color.BLACK
        val backgroundColor =
            if (isReadableModeEnabled) resources.getColor(R.color.gray) else Color.WHITE
        val textSize =
            (if (isReadableModeEnabled) 20 else 16).toFloat() // Adjust font size as needed

        // Iterate through views and apply changes
        val rootView = findViewById<View>(R.id.content)
        applyChangesRecursive(rootView, textColor, backgroundColor, textSize)
    }
    // Recursive function to apply changes to all child views
    fun applyChangesRecursive(view: View,
                                      textColor: Int,
                                      backgroundColor: Int,
                                      textSize: Float
    ) {
        if (view is ViewGroup) {
            val viewGroup = view
            for (i in 0 until viewGroup.childCount) {
                applyChangesRecursive(viewGroup.getChildAt(i), textColor, backgroundColor, textSize)
            }
        } else if (view is TextView) {
            val textView = view
            textView.setTextColor(textColor)
            textView.setBackgroundColor(backgroundColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
        }
    }


}
