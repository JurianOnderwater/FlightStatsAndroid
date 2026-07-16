package com.example.flightstats;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScannerActivity extends AppCompatActivity {

    public static final String EXTRA_ORIGIN = "origin";
    public static final String EXTRA_DESTINATION = "destination";
    public static final String EXTRA_AIRLINE = "airline";
    public static final String EXTRA_FLIGHT_NUM = "flight_num";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_SEAT = "seat";
    public static final String EXTRA_CLASS = "class";

    private PreviewView viewFinder;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startCamera();
                else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processImageFromGallery(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);

        viewFinder = findViewById(R.id.view_finder);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure scanner to read PDF417, Aztec, and QR codes (all common for BCBP)
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_PDF417,
                        Barcode.FORMAT_AZTEC,
                        Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);

        android.view.View btnClose = findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnClose, (v, insets) -> {
                int topInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
                lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density) + topInset;
                v.setLayoutParams(lp);
                return insets;
            });
        }

        android.view.View btnGallery = findViewById(R.id.btn_gallery);
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnGallery, (v, insets) -> {
                int bottomInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
                lp.bottomMargin = (int) (48 * getResources().getDisplayMetrics().density) + bottomInset;
                v.setLayoutParams(lp);
                return insets;
            });
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null) handleBarcodeStr(rawValue);
                    }
                })
                .addOnFailureListener(e -> {})
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processImageFromGallery(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (barcodes.isEmpty()) {
                            Toast.makeText(this, "No barcode found in image", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null && handleBarcodeStr(rawValue)) return; // Done on first success
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean handleBarcodeStr(String rawValue) {
        BcbpParser.Result r = BcbpParser.parse(rawValue);
        if (r.success) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_ORIGIN, r.origin);
            intent.putExtra(EXTRA_DESTINATION, r.destination);
            intent.putExtra(EXTRA_AIRLINE, r.airline);
            intent.putExtra(EXTRA_FLIGHT_NUM, r.flightNumber);
            intent.putExtra(EXTRA_DATE, r.date);
            intent.putExtra(EXTRA_SEAT, r.seat);
            intent.putExtra(EXTRA_CLASS, r.seatClass);
            setResult(RESULT_OK, intent);
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanner.close();
    }
}
