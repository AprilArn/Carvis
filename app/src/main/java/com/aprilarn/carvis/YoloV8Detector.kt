package com.aprilarn.carvis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.aprilarn.carvis.MetaDataUtils.extractNamesFromLabelFile
import com.aprilarn.carvis.MetaDataUtils.extractNamesFromMetadata
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

enum class DelegateType { GPU, NNAPI, CPU }

class YoloV8Detector (

    private val context: Context,
    private val modelPath: String,
    private val labelPath: String?,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit

) {

    // Daftar nama-nama kelas objek
    private var labels = mutableListOf<String>()
    // Interpreter TensorFlow Lite
    private var interpreter: Interpreter
    // Delegate yang sedang digunakan (GPU, NNAPI, atau CPU), defaultnya CPU
    var currentDelegate: DelegateType = DelegateType.CPU
        private set

    // Dimensi input tensor model
    private var tensorWidth = 0
    private var tensorHeight = 0
    // Dimensi output tensor model
    private var numChannel = 0
    private var numElements = 0

    // ImageProcessor untuk melakukan preprocessing pada gambar input
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    // Fungsi untuk memeriksa apakah GPU Delegate didukung oleh perangkat
    fun isGpuSupported(): Boolean = CompatibilityList().isDelegateSupportedOnThisDevice
    // Fungsi untuk memeriksa apakah NNAPI didukung (NNAPI selalu tersedia, efisiensi bervariasi)
    fun isNnapiSupported(): Boolean = true

    init {

        // Membuat interpreter dengan delegate terbaik yang tersedia secara otomatis
        interpreter = createInterpreter(getBestAvailableDelegate())

        // Memuat file model TensorFlow Lite
        val model = FileUtil.loadMappedFile(context, modelPath)
        // Mendapatkan bentuk (shape) tensor input dan output dari interpreter
        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape = interpreter.getOutputTensor(0)?.shape()

        // Mengekstrak nama label dari metadata model (jika ada)
        val labelsFromMeta = extractNamesFromMetadata(model)
        labels = if (labelsFromMeta.isNotEmpty()) {
            labelsFromMeta.toMutableList()
        } else {
            // Jika tidak ada di metadata, coba dari file label eksternal
            if (labelPath != null) extractNamesFromLabelFile(context, labelPath).toMutableList()
            else {
                message("Model not contains metadata, provide LABELS_PATH in Model.kt")
                MetaDataUtils.TEMP_CLASSES.toMutableList()
            }
        }

        // Mengambil dimensi input tensor (lebar dan tinggi)
        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]
            // Penyesuaian jika inputShape memiliki dimensi tambahan (misalnya untuk batch size)
            if (inputShape[1] == 3) { // Mengasumsikan format [batch, channel, height, width]
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        // Mengambil dimensi output tensor (jumlah channel dan elemen)
        if (outputShape != null) {
            numChannel = outputShape[1]
            numElements = outputShape[2]
        }
    }

    // Fungsi untuk mendapatkan delegate terbaik yang tersedia berdasarkan dukungan hardware
    private fun getBestAvailableDelegate(): DelegateType {

        return when {
            isGpuSupported() -> DelegateType.GPU
            isNnapiSupported() -> DelegateType.NNAPI
            else -> DelegateType.CPU
        }

    }

    // Fungsi untuk membuat Interpreter TensorFlow Lite dengan delegate tertentu
    private fun createInterpreter(delegate: DelegateType): Interpreter {

        val options = Interpreter.Options()
        when (delegate) {
            DelegateType.GPU -> {
                currentDelegate = DelegateType.GPU
                val compatList = CompatibilityList()
                val gpuOptions = compatList.bestOptionsForThisDevice
                options.addDelegate(GpuDelegate(gpuOptions))
            }
            DelegateType.NNAPI -> {
                currentDelegate = DelegateType.NNAPI
                options.setUseNNAPI(true)
            }
            DelegateType.CPU -> {
                currentDelegate = DelegateType.CPU
                options.setNumThreads(4)
            }
        }
        val model = FileUtil.loadMappedFile(context, modelPath) // Memuat file model lagi
        return Interpreter(model, options) // Membuat dan mengembalikan interpreter baru

    }

    // Fungsi untuk me-restart interpreter dengan delegate yang berbeda
    fun restart(delegate: DelegateType) {

        interpreter.close()
        interpreter = createInterpreter(delegate)

    }

    // Fungsi untuk menutup interpreter dan melepaskan sumber daya
    fun close() {

        interpreter.close()

    }

    // Fungsi utama untuk melakukan deteksi objek pada frame bitmap
    fun detect(frame: Bitmap) {

        // Jika dimensi tensor belum diinisialisasi, keluar dari fungsi
        if (tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()

        // Mengubah ukuran bitmap input agar sesuai dengan dimensi input tensor model
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        // Membuat TensorImage dan memuat bitmap yang sudah diubah ukurannya
        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)

        // Memproses gambar (normalisasi, casting tipe data)
        val processedImage = imageProcessor.process(tensorImage)

        // Membuat TensorBuffer untuk menampung output dari model
        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)

        // Menjalankan inferensi model
        interpreter.run(processedImage.buffer, output.buffer)

        // Menghitung waktu inferensi total
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        // Mendapatkan daftar bounding box terbaik dari output model
        val bestBoxes = bestBox(output.floatArray)
        // Memberi tahu listener hasil deteksi
        if (bestBoxes == null) detectorListener.onEmptyDetect()
        else detectorListener.onDetect(bestBoxes, inferenceTime)

    }

    // Fungsi untuk memproses output mentah dari model dan mendapatkan bounding box terbaik
    private fun bestBox(array: FloatArray): List<BoundingBox>? {

        val boundingBoxes = mutableListOf<BoundingBox>() // Daftar untuk menyimpan bounding box yang memenuhi syarat
        // Iterasi melalui setiap 'element' (anchor/grid cell) dalam output model
        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1 // Indeks kelas dengan confidence tertinggi
            var j = 4 // Mulai dari indeks 4 karena indeks 0-3 adalah koordinat dan ukuran bounding box
            var arrayIdx = c + numElements * j // Menghitung indeks array untuk confidence kelas pertama
            // Iterasi melalui confidence score untuk setiap kelas
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) { // Jika confidence saat ini lebih tinggi dari maxConf
                    maxConf = array[arrayIdx]    // Update maxConf
                    maxIdx = j - 4               // Update maxIdx (indeks kelas)
                }
                j++ // Lanjut ke kelas berikutnya
                arrayIdx += numElements // Lanjut ke elemen berikutnya dalam channel yang sama
            }
            // Jika confidence tertinggi melebihi CONFIDENCE_THRESHOLD
            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx] // Dapatkan nama kelas
                val cx = array[c]                  // Koordinat tengah X dari bounding box
                val cy = array[c + numElements]    // Koordinat tengah Y dari bounding box
                val w = array[c + numElements * 2] // Lebar bounding box
                val h = array[c + numElements * 3] // Tinggi bounding box
                // Menghitung koordinat pojok (x1, y1, x2, y2) dari (cx, cy, w, h)
                val x1 = cx - w / 2F
                val y1 = cy - h / 2F
                val x2 = cx + w / 2F
                val y2 = cy + h / 2F
                // Memastikan koordinat berada dalam rentang 0-1 (valid)
                if (x1 in 0f..1f && y1 in 0f..1f && x2 in 0f..1f && y2 in 0f..1f) {
                    // Menambahkan BoundingBox ke daftar
                    boundingBoxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, clsName))
                }
            }
        }
        // Jika tidak ada bounding box yang ditemukan, kembalikan null.
        // Jika ada, terapkan Non-Maximum Suppression (NMS).
        return if (boundingBoxes.isEmpty()) null else applyNMS(boundingBoxes)

    }

    // Fungsi untuk menerapkan Non-Maximum Suppression (NMS)
    // NMS digunakan untuk menghilangkan bounding box yang tumpang tindih secara berlebihan
    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {

        // Mengurutkan bounding box berdasarkan confidence score secara menurun
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>() // Daftar untuk menyimpan bounding box yang terpilih

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes[0] // Ambil bounding box dengan confidence tertinggi
            sortedBoxes.removeAt(0)    // Hapus dari daftar yang tersisa

            selectedBoxes.add(first)   // Tambahkan ke daftar yang terpilih
            // Hapus semua bounding box lain yang memiliki IoU (Intersection over Union)
            // di atas IOU_THRESHOLD dengan bounding box yang baru saja dipilih
            sortedBoxes.removeAll { calculateIoU(first, it) >= IOU_THRESHOLD }
        }

        return selectedBoxes

    }

    // Fungsi untuk menghitung Intersection over Union (IoU) antara dua bounding box
    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {

        // Menghitung koordinat area irisan (intersection)
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        // Menghitung area irisan
        val intersection = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        // Menghitung area masing-masing bounding box
        val area1 = box1.w * box1.h
        val area2 = box2.w * box2.h
        // Menghitung IoU = Area Irisan / (Area Box1 + Area Box2 - Area Irisan)
        return intersection / (area1 + area2 - intersection)

    }

    // Interface untuk listener detektor
    interface DetectorListener {

        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)

    }

    companion object {

        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.6f
        private const val IOU_THRESHOLD = 0.5f

    }

}