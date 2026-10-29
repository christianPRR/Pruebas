package com.example.timestampcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var infoText: TextView
    private lateinit var noteInput: EditText
    private lateinit var captureButton: Button

    private var imageCapture: ImageCapture? = null
    private var lastLocation: Location? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            infoText.text = buildInfoText()
            handler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCameraPermission()) {
                startCamera()
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        infoText = findViewById(R.id.infoText)
        noteInput = findViewById(R.id.noteInput)
        captureButton = findViewById(R.id.captureButton)

        captureButton.setOnClickListener { takePhoto() }

        if (hasCameraPermission()) {
            startCamera()
            startLocationUpdates()
        } else {
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .removeUpdates(locationListener)
        } catch (_: Exception) {
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.getLastKnownLocation(provider)?.let { lastLocation = it }
                    lm.requestLocationUpdates(provider, 2000L, 0f, locationListener)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun locationLine(): String {
        val loc = lastLocation
        return if (loc != null) {
            String.format(Locale.US, "Lat: %.6f  Lon: %.6f", loc.latitude, loc.longitude)
        } else {
            "Ubicación: buscando..."
        }
    }

    private fun buildInfoText(): String =
        dateFormat.format(Date()) + "\n" + locationLine()

    private fun takePhoto() {
        val capture = imageCapture ?: return

        // Se toman los datos en el momento exacto del disparo (hora del sistema del teléfono).
        val lines = mutableListOf(dateFormat.format(Date()), locationLine())
        val note = noteInput.text.toString().trim()
        if (note.isNotEmpty()) lines.add(note)

        captureButton.isEnabled = false

        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val original = image.toBitmap()
                    val stamped = stamp(original, rotation, lines)
                    val saved = saveToGallery(stamped)
                    runOnUiThread {
                        captureButton.isEnabled = true
                        Toast.makeText(
                            this@MainActivity,
                            if (saved) "Foto guardada en Galería (Pictures/TimestampCamera)"
                            else "No se pudo guardar la foto",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        captureButton.isEnabled = true
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    captureButton.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        "Error al capturar: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun stamp(src: Bitmap, rotation: Int, lines: List<String>): Bitmap {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        val bmp = rotated.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val size = bmp.width / 28f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        val pad = size * 0.6f
        val lineHeight = size * 1.25f
        val boxHeight = pad * 2 + lineHeight * lines.size

        val bg = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        canvas.drawRect(0f, bmp.height - boxHeight, bmp.width.toFloat(), bmp.height.toFloat(), bg)

        lines.forEachIndexed { i, text ->
            val y = bmp.height - boxHeight + pad + lineHeight * (i + 0.8f)
            canvas.drawText(text, pad, y, paint)
        }
        return bmp
    }

    private fun saveToGallery(bitmap: Bitmap): Boolean {
        val name = "TS_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TimestampCamera")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: return false
        return true
    }
}
