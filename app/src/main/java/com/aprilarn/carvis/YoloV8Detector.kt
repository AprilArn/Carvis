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

    private var labels = mutableListOf<String>()
    private var interpreter: Interpreter
    var currentDelegate: DelegateType = DelegateType.CPU
        private set

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun isGpuSupported(): Boolean = CompatibilityList().isDelegateSupportedOnThisDevice
    fun isNnapiSupported(): Boolean = true // NNAPI always available, but may not be efficient

    init {
        interpreter = createInterpreter(getBestAvailableDelegate())

        val model = FileUtil.loadMappedFile(context, modelPath)
        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape = interpreter.getOutputTensor(0)?.shape()

        val labelsFromMeta = extractNamesFromMetadata(model)
        labels = if (labelsFromMeta.isNotEmpty()) {
            labelsFromMeta.toMutableList()
        } else {
            if (labelPath != null) extractNamesFromLabelFile(context, labelPath).toMutableList()
            else {
                message("Model not contains metadata, provide LABELS_PATH in Model.kt")
                MetaDataUtils.TEMP_CLASSES.toMutableList()
            }
        }

        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]
            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        if (outputShape != null) {
            numChannel = outputShape[1]
            numElements = outputShape[2]
        }
    }

    private fun getBestAvailableDelegate(): DelegateType {
        return when {
            isGpuSupported() -> DelegateType.GPU
            isNnapiSupported() -> DelegateType.NNAPI
            else -> DelegateType.CPU
        }
    }

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
        val model = FileUtil.loadMappedFile(context, modelPath)
        return Interpreter(model, options)
    }

    fun restart(delegate: DelegateType) {
        interpreter.close()
        interpreter = createInterpreter(delegate)
    }

    fun close() {
        interpreter.close()
    }

    fun detect(frame: Bitmap) {
        if (tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter.run(processedImage.buffer, output.buffer)

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        val bestBoxes = bestBox(output.floatArray)
        if (bestBoxes == null) detectorListener.onEmptyDetect()
        else detectorListener.onDetect(bestBoxes, inferenceTime)
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx]
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - w / 2F
                val y1 = cy - h / 2F
                val x2 = cx + w / 2F
                val y2 = cy + h / 2F
                if (x1 in 0f..1f && y1 in 0f..1f && x2 in 0f..1f && y2 in 0f..1f) {
                    boundingBoxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, clsName))
                }
            }
        }

        return if (boundingBoxes.isEmpty()) null else applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes[0]
            sortedBoxes.removeAt(0)

            selectedBoxes.add(first)
            sortedBoxes.removeAll { calculateIoU(first, it) >= IOU_THRESHOLD }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersection = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val area1 = box1.w * box1.h
        val area2 = box2.w * box2.h
        return intersection / (area1 + area2 - intersection)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val IOU_THRESHOLD = 0.5f
    }

}