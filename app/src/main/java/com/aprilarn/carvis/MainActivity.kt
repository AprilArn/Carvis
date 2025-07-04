package com.aprilarn.carvis

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aprilarn.carvis.Model.LABEL_PATH
import com.aprilarn.carvis.Model.MODEL_PATH
import com.aprilarn.carvis.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), YoloV8Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: YoloV8Detector? = null
    private var activeDelegate: DelegateType? = null

    private lateinit var predictedAdapter: PredictedAdapter
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if(OpenCVLoader.initLocal()) {
            Log.i("opencv", "Successfully integrated")
        }

        // Mencegah layar mati
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // FullScreen
        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        // Set layout manager
        predictedAdapter = PredictedAdapter(emptyList())
        binding.predictionList.layoutManager = LinearLayoutManager(this)
        binding.predictionList.adapter = predictedAdapter

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraExecutor.execute {
            detector = YoloV8Detector(baseContext, MODEL_PATH, LABEL_PATH, this) {
                toast(it)
            }

            // Checking best resource
            runOnUiThread {
                // Dapatkan delegate yang terpilih otomatis dari detector
                val initialDelegate = detector?.currentDelegate
                if (initialDelegate != null) {
                    // Buat nama delegate menjadi string
                    val delegateName = when (initialDelegate) {
                        DelegateType.GPU -> "GPU (Fastest)"
                        DelegateType.NNAPI -> "NNAPI (Accelerated)"
                        DelegateType.CPU -> "CPU (Standard)"
                    }
                    // Tampilkan pesan pop-up
                    toast("Auto-selected: $delegateName")
                }

                val isGpuSupported = detector?.isGpuSupported() ?: false
                val isNnapiSupported = detector?.isNnapiSupported() ?: false

                // GPU
                binding.gpuButton.isEnabled = isGpuSupported
                binding.gpuButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this,
                        if (isGpuSupported) R.color.faded_blue else R.color.gray)
                )

                // NNAPI
                binding.nnapiButton.isEnabled = isNnapiSupported
                binding.nnapiButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this,
                        if (isNnapiSupported) R.color.faded_blue else R.color.gray)
                )

                // CPU selalu aktif
                binding.cpuButton.isEnabled = true
                binding.cpuButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.faded_blue)
                )

                // Set warna tombol sesuai delegate aktif
                when (detector?.currentDelegate) {
                    DelegateType.GPU -> updateButtonColors(binding.gpuButton)
                    DelegateType.NNAPI -> updateButtonColors(binding.nnapiButton)
                    DelegateType.CPU -> updateButtonColors(binding.cpuButton)
                    else -> updateButtonColors(null) // Tidak ada yang aktif
                }
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        bindListeners()

    }

    // Function to handle button clicks and delegate toggling
    private fun bindListeners() {

        val toggleDelegate = { delegate: DelegateType, button: Button ->
            // 1. Buat pesan berdasarkan delegate yang dipilih
            val message = when (delegate) {
                DelegateType.GPU -> "Processing with GPU (Fastest)"
                DelegateType.NNAPI -> "Processing with NNAPI (Accelerated)"
                DelegateType.CPU -> "Processing with CPU (Standard)"
            }
            // 2. Tampilkan pesan menggunakan fungsi toast
            toast(message)

            // Kode yang sudah ada sebelumnya tetap dipertahankan
            cameraExecutor.submit {
                detector?.restart(delegate)
            }
            activeDelegate = delegate
            updateButtonColors(button)
        }

        binding.gpuButton.setOnClickListener {
            toggleDelegate(DelegateType.GPU, binding.gpuButton)
        }

        binding.nnapiButton.setOnClickListener {
            toggleDelegate(DelegateType.NNAPI, binding.nnapiButton)
        }

        binding.cpuButton.setOnClickListener {
            toggleDelegate(DelegateType.CPU, binding.cpuButton)
        }

    }

    // Function to update button colors based on selection
    private fun updateButtonColors(selected: Button?) {

        val buttons = listOf(binding.gpuButton, binding.nnapiButton, binding.cpuButton)
        buttons.forEach {
            if (it.isEnabled) {
                it.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                        this,
                        if (it == selected) R.color.blue else R.color.faded_blue
                    )
                )
            }
        }

    }

    // Function to start the camera
    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))

    }

    // Function to bind camera use cases
    private fun bindCameraUseCases() {

        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val rotation = binding.viewFinder.display.rotation
        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector?.detect(rotatedBitmap)

            // Steering advice using lane detection
            val laneLines = LaneDetector.detectLaneWithLines(rotatedBitmap)
            runOnUiThread {
                binding.overlay.setImageSize(rotatedBitmap.width, rotatedBitmap.height)
                binding.overlay.setLaneLines(laneLines) // Ini akan memicu invalidate() di OverlayView

                if (laneLines.size == 2) {
                    val leftX = laneLines[0].first.x
                    val rightX = laneLines[1].first.x
                    val midX = ((leftX + rightX) / 2).toInt()
                    val centerX = rotatedBitmap.width / 2
                    val offset = midX - centerX

                    val direction = when {
                        offset < -38 -> "\u25C0 Turn Left"
                        offset > 38 -> "Turn Right \u25B6"
                        else -> ""
                    }

                    val scaledMidX = midX * binding.overlay.width / rotatedBitmap.width
                    binding.overlay.setSteeringInfo(scaledMidX, direction)
                } else {
                    // Jika laneLines bukan 2, panggil setSteeringInfo tanpa argumen
                    // Ini akan memicu logika timeout di OverlayView untuk membersihkan info kemudi
                    binding.overlay.setSteeringInfo()
                }
            }

        }
        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.surfaceProvider = binding.viewFinder.surfaceProvider
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }

    }

    // Function to check if all required permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {

        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED

    }

    // Function to handle permission request result
    private val requestPermissionLauncher = registerForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }

    }

    // Function to show a toast message
    private fun toast(message: String) {

        runOnUiThread {
            Toast.makeText(baseContext, message, Toast.LENGTH_LONG).show()
        }

    }

    override fun onDestroy() {

        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()

    }

    override fun onResume() {

        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

    }

    override fun onEmptyDetect() {

        runOnUiThread {
            binding.overlay.clearBoundingBoxes()

            // Reset inference time ke --ms
            binding.inferenceTime.text = "--ms"

            // Kosongkan adapter (tidak ada prediksi yang ditampilkan)
            binding.predictionList.adapter = PredictedAdapter(emptyList())
            binding.overlay.invalidate() // Pastikan overlay digambar ulang
        }

    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {

        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.setResults(boundingBoxes)

            // Ambil nama kelas dari hasil deteksi yang confidence-nya lebih dari 80%
            val predictedNames = boundingBoxes
                .filter { it.cnf > 0.8f }
                .map { it.clsName }
                .distinct()

            if (predictedNames != predictedAdapter.items) {
                predictedAdapter.updateItems(predictedNames)
            }

            // Log.d("DEBUG", "Predicted names: $predictedNames")

            binding.predictionList.adapter = PredictedAdapter(predictedNames)
        }

    }

    companion object {

        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()

    }

}
