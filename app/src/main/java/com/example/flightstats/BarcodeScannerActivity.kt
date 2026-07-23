package com.example.flightstats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORIGIN = "origin"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_AIRLINE = "airline"
        const val EXTRA_FLIGHT_NUM = "flight_num"
        const val EXTRA_DATE = "date"
        const val EXTRA_SEAT = "seat"
        const val EXTRA_CLASS = "class"
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanner: BarcodeScanner

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImageFromGallery(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        viewFinder = findViewById(R.id.view_finder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        scanner = BarcodeScanning.getClient(options)

        val btnClose = findViewById<View>(R.id.btn_close)
        btnClose?.let {
            it.setOnClickListener { finish() }
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val lp = v.layoutParams as ConstraintLayout.LayoutParams
                lp.topMargin = (16 * resources.displayMetrics.density).toInt() + topInset
                v.layoutParams = lp
                insets
            }
        }

        val btnGallery = findViewById<View>(R.id.btn_gallery)
        btnGallery?.let {
            it.setOnClickListener { pickImageLauncher.launch("image/*") }
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val lp = v.layoutParams as ConstraintLayout.LayoutParams
                lp.bottomMargin = (48 * resources.displayMetrics.density).toInt() + bottomInset
                v.layoutParams = lp
                insets
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(viewFinder.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (rawValue != null) {
                        if (handleBarcodeStr(rawValue)) break
                    }
                }
            }
            .addOnFailureListener {}
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        Toast.makeText(this, "No barcode found in image", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && handleBarcodeStr(rawValue)) return@addOnSuccessListener
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleBarcodeStr(rawValue: String): Boolean {
        val r = BcbpParser.parse(rawValue)
        if (r.success) {
            val intent = Intent().apply {
                putExtra(EXTRA_ORIGIN, r.origin)
                putExtra(EXTRA_DESTINATION, r.destination)
                putExtra(EXTRA_AIRLINE, r.airline)
                putExtra(EXTRA_FLIGHT_NUM, r.flightNumber)
                putExtra(EXTRA_DATE, r.date)
                putExtra(EXTRA_SEAT, r.seat)
                putExtra(EXTRA_CLASS, r.seatClass)
            }
            setResult(RESULT_OK, intent)
            finish()
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }
}
