//package com.aprilarn.carvis
//
//import android.content.Context
//import android.graphics.Canvas
//import android.graphics.Color
//import android.graphics.Paint
//import android.graphics.Rect
//import android.util.AttributeSet
//import android.view.View
//import androidx.core.content.ContextCompat
//
//class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
//
//    private var results = listOf<BoundingBox>()
//    private var boxPaint = Paint()
//    private var textBackgroundPaint = Paint()
//    private var textPaint = Paint()
//
//    private var bounds = Rect()
//
//    init {
//        initPaints()
//    }
//
//    fun clear() {
//        results = listOf()
//        textPaint.reset()
//        textBackgroundPaint.reset()
//        boxPaint.reset()
//        invalidate()
//        initPaints()
//    }
//
//    private fun initPaints() {
//        textBackgroundPaint.color = ContextCompat.getColor(context!!, R.color.pink)
//        textBackgroundPaint.style = Paint.Style.FILL
//        textBackgroundPaint.textSize = 50f
//
//        textPaint.color = Color.WHITE
//        textPaint.style = Paint.Style.FILL
//        textPaint.textSize = 50f
//
//        boxPaint.color = ContextCompat.getColor(context!!, R.color.pink)
//        boxPaint.strokeWidth = 8F
//        boxPaint.style = Paint.Style.STROKE
//    }
//
//    override fun draw(canvas: Canvas) {
//        super.draw(canvas)
//
//        results.forEach { result ->
//            val left = result.x1 * width
//            val top = result.y1 * height
//            val right = result.x2 * width
//            val bottom = result.y2 * height
//
//            // Gambar bounding box
//            canvas.drawRect(left, top, right, bottom, boxPaint)
//
//            val text = result.clsName
//            val padding = BOUNDING_RECT_TEXT_PADDING
//
//            // Hitung lebar dan tinggi teks
//            val textWidth = textPaint.measureText(text)
//            textPaint.getTextBounds(text, 0, text.length, bounds)
//            val textHeight = bounds.height()
//
//            // Default posisi background teks: di atas bounding box
//            var bgLeft = left
//            var bgTop = top - textHeight - 2 * padding
//            var bgRight = bgLeft + textWidth + 2 * padding
//            var bgBottom = top
//
//            // Koreksi kanan
//            if (bgRight > width) {
//                val overflow = bgRight - width
//                bgLeft -= overflow
//                bgRight = width.toFloat()
//            }
//
//            // Koreksi kiri
//            if (bgLeft < 0f) {
//                bgLeft = 0f
//                bgRight = textWidth + 2 * padding
//            }
//
//            // Koreksi atas: geser turun sedikit supaya tidak keluar layar, tapi tetap di atas bounding box
//            if (bgTop < 0f) {
//                val delta = 0f - bgTop
//                bgTop += delta
//                bgBottom += delta
//            }
//
//            // Hitung posisi teks (baseline)
//            val textX = bgLeft + padding
//            val textY = bgBottom - padding - bounds.bottom
//
//            // Gambar background dan teks
//            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBackgroundPaint)
//            canvas.drawText(text, textX, textY, textPaint)
//        }
//    }
//
//    fun setResults(boundingBoxes: List<BoundingBox>) {
//        results = boundingBoxes
//        invalidate()
//    }
//
//    companion object {
//        private const val BOUNDING_RECT_TEXT_PADDING = 8
//    }
//}

//package com.aprilarn.carvis
//
//import android.content.Context
//import android.graphics.*
//import android.util.AttributeSet
//import android.view.View
//import androidx.core.content.ContextCompat
//import org.opencv.core.Point
//
//class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
//
//    private var results = listOf<BoundingBox>()
//    private var laneLines = listOf<Pair<Point, Point>>()
//    private var imageWidth = 1
//    private var imageHeight = 1
//
//    private var steeringDirection: String? = null
//    private var laneMidX: Int? = null
//    private var lastSteeringUpdateTime: Long = 0L
//    private val steeringTimeoutMs = 1500L // 1.5 detik
//
//    private var boxPaint = Paint()
//    private var textBackgroundPaint = Paint()
//    private var textPaint = Paint()
//    private var bounds = Rect()
//
//    init {
//        initPaints()
//    }
//
//    fun clear() {
//        results = listOf()
//        laneLines = listOf()
//        steeringDirection = null
//        laneMidX = null
//        textPaint.reset()
//        textBackgroundPaint.reset()
//        boxPaint.reset()
//        invalidate()
//        initPaints()
//    }
//
//    private fun initPaints() {
//        textBackgroundPaint.color = ContextCompat.getColor(context!!, R.color.pink)
//        textBackgroundPaint.style = Paint.Style.FILL
//        textBackgroundPaint.textSize = 50f
//
//        textPaint.color = Color.WHITE
//        textPaint.style = Paint.Style.FILL
//        textPaint.textSize = 50f
//
//        boxPaint.color = ContextCompat.getColor(context!!, R.color.pink)
//        boxPaint.strokeWidth = 8F
//        boxPaint.style = Paint.Style.STROKE
//    }
//
//    override fun draw(canvas: Canvas) {
//        super.draw(canvas)
//
//        // --- 1. Gambar bounding box YOLO ---
//        results.forEach { result ->
//            val left = result.x1 * width
//            val top = result.y1 * height
//            val right = result.x2 * width
//            val bottom = result.y2 * height
//
//            canvas.drawRect(left, top, right, bottom, boxPaint)
//
//            val text = result.clsName
//            val padding = BOUNDING_RECT_TEXT_PADDING
//
//            val textWidth = textPaint.measureText(text)
//            textPaint.getTextBounds(text, 0, text.length, bounds)
//            val textHeight = bounds.height()
//
//            var bgLeft = left
//            var bgTop = top - textHeight - 2 * padding
//            var bgRight = bgLeft + textWidth + 2 * padding
//            var bgBottom = top
//
//            if (bgRight > width) {
//                val overflow = bgRight - width
//                bgLeft -= overflow
//                bgRight = width.toFloat()
//            }
//
//            if (bgLeft < 0f) {
//                bgLeft = 0f
//                bgRight = textWidth + 2 * padding
//            }
//
//            if (bgTop < 0f) {
//                val delta = 0f - bgTop
//                bgTop += delta
//                bgBottom += delta
//            }
//
//            val textX = bgLeft + padding
//            val textY = bgBottom - padding - bounds.bottom
//
//            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBackgroundPaint)
//            canvas.drawText(text, textX, textY, textPaint)
//        }
//
//        // --- 2. Gambar lane Prediction ---
//        val lanePaint = Paint().apply {
//            color = Color.GREEN
//            strokeWidth = 6f
//            style = Paint.Style.STROKE
//        }
//
//        laneLines.forEach { (start, end) ->
//            val sx = ((start.x / imageWidth) * width).toFloat()
//            val sy = ((start.y / imageHeight) * height).toFloat()
//            val ex = ((end.x / imageWidth) * width).toFloat()
//            val ey = ((end.y / imageHeight) * height).toFloat()
//
//            canvas.drawLine(sx, sy, ex, ey, lanePaint)
//        }
//
//        // --- 3. Steering Advice ---
//        val centerX = width / 2
//        val centerY = (height * 0.84f)
//
//        val paintLine = Paint().apply {
//            color = Color.YELLOW
//            strokeWidth = 4f
//            style = Paint.Style.STROKE
//        }
//
//        val paintIndicator = Paint().apply {
//            color = Color.GREEN
//            strokeWidth = 6f
//        }
//
//        val paintBoundary = Paint().apply {
//            color = Color.RED
//            strokeWidth = 4f
//        }
//
//        val paintText = Paint().apply {
//            color = Color.WHITE
//            textSize = 42f
//            isAntiAlias = true
//            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//        }
//
//        laneMidX?.let { midX ->
//            // Garis horizontal dari tengah kamera ke titik tengah lane
//            canvas.drawLine(centerX.toFloat(), centerY, midX.toFloat(), centerY, paintLine)
//
//            // Garis koreksi di ujung garis kuning tengah prediksi
//            canvas.drawLine(midX.toFloat(), centerY - 10, midX.toFloat(), centerY + 10, paintIndicator)
//
//            // Tambahan: Garis vertikal hijau di titik prediksi kiri dan kanan
//            if (laneLines.size == 2) {
//                val leftX = ((laneLines[0].first.x / imageWidth) * width).toFloat()
//                val rightX = ((laneLines[1].first.x / imageWidth) * width).toFloat()
//                listOf(leftX, rightX, midX.toFloat()).forEach { x ->
//                    canvas.drawLine(x, centerY - 14, x, centerY + 14, paintIndicator)
//                }
//            }
//
//            // Teks arah belok
//            val now = System.currentTimeMillis()
//            if (steeringDirection != null && now - lastSteeringUpdateTime < steeringTimeoutMs) {
//                val textWidth = paintText.measureText(steeringDirection!!)
//                canvas.drawText(steeringDirection!!, (width - textWidth) / 2, centerY - 40, paintText)
//            }
//        }
//
//        // Garis vertikal dari bawah ke tengah
//        canvas.drawLine(centerX.toFloat(), height.toFloat(), centerX.toFloat(), centerY, paintLine)
//
//        // Garis batas belok kiri/kanan
//        listOf(centerX - 100, centerX + 100).forEach { x ->
//            canvas.drawLine(x.toFloat(), centerY - 10, x.toFloat(), centerY + 10, paintBoundary)
//        }
//    }
//
//    fun setResults(boundingBoxes: List<BoundingBox>) {
//        results = boundingBoxes
//        invalidate()
//    }
//
//    fun setLaneLines(lines: List<Pair<Point, Point>>) {
//        laneLines = lines
//        invalidate()
//    }
//
//    fun setImageSize(width: Int, height: Int) {
//        imageWidth = width
//        imageHeight = height
//    }
//
//    fun setSteeringInfo(midX: Int? = null, direction: String? = null) {
//        if (midX != null && direction != null) {
//            laneMidX = midX
//            steeringDirection = direction
//            lastSteeringUpdateTime = System.currentTimeMillis()
//        }
//        invalidate()
//    }
//
//    companion object {
//        private const val BOUNDING_RECT_TEXT_PADDING = 8
//    }
//}

// OverlayView.kt

package com.aprilarn.carvis

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.opencv.core.Point

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var laneLines = listOf<Pair<Point, Point>>()
    private var imageWidth = 1
    private var imageHeight = 1

    private var steeringDirection: String? = null
    private var laneMidX: Int? = null
    private var lastSteeringUpdateTime: Long = 0L
    private val steeringTimeoutMs = 1500L // 1.5 detik

    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var bounds = Rect()

    init {
        initPaints()
    }

    // Ubah fungsi clear() untuk lebih spesifik
    fun clear() {
        clearBoundingBoxes()
        clearLaneInfo()
        invalidate() // Invalidate sekali setelah semua dibersihkan
        initPaints() // Pastikan paint diinisialisasi ulang jika diperlukan (opsional, tergantung bagaimana clear dipanggil)
    }

    fun clearBoundingBoxes() {
        results = listOf()
        // Tidak mereset paint di sini, karena paint digunakan bersama
    }

    fun clearLaneInfo() {
        laneLines = listOf()
        steeringDirection = null
        laneMidX = null
        lastSteeringUpdateTime = 0L // Reset the timer as well
    }

    private fun initPaints() {
        textBackgroundPaint.color = ContextCompat.getColor(context!!, R.color.pink)
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.pink)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // --- 1. Gambar bounding box YOLO ---
        results.forEach { result ->
            val left = result.x1 * width
            val top = result.y1 * height
            val right = result.x2 * width
            val bottom = result.y2 * height

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = result.clsName
            val padding = BOUNDING_RECT_TEXT_PADDING

            val textWidth = textPaint.measureText(text)
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val textHeight = bounds.height()

            var bgLeft = left
            var bgTop = top - textHeight - 2 * padding
            var bgRight = bgLeft + textWidth + 2 * padding
            var bgBottom = top

            if (bgRight > width) {
                val overflow = bgRight - width
                bgLeft -= overflow
                bgRight = width.toFloat()
            }

            if (bgLeft < 0f) {
                bgLeft = 0f
                bgRight = textWidth + 2 * padding
            }

            if (bgTop < 0f) {
                val delta = 0f - bgTop
                bgTop += delta
                bgBottom += delta
            }

            val textX = bgLeft + padding
            val textY = bgBottom - padding - bounds.bottom

            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBackgroundPaint)
            canvas.drawText(text, textX, textY, textPaint)
        }

        // --- 2. Gambar lane Prediction ---
        val lanePaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }

        laneLines.forEach { (start, end) ->
            val sx = ((start.x / imageWidth) * width).toFloat()
            val sy = ((start.y / imageHeight) * height).toFloat()
            val ex = ((end.x / imageWidth) * width).toFloat()
            val ey = ((end.y / imageHeight) * height).toFloat()

            canvas.drawLine(sx, sy, ex, ey, lanePaint)
        }

        // --- 3. Steering Advice ---
        val centerX = width / 2
        val centerY = (height * 0.84f)

        val paintLine = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        val paintIndicator = Paint().apply {
            color = Color.GREEN
            strokeWidth = 6f
        }

        val paintBoundary = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
        }

        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = 42f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Hanya gambar steering advice jika ada informasi dan belum timeout
        val now = System.currentTimeMillis()
        if (laneMidX != null && steeringDirection != null && now - lastSteeringUpdateTime < steeringTimeoutMs) {
            val midX = laneMidX!!

            // Garis horizontal dari tengah kamera ke titik tengah lane
            canvas.drawLine(centerX.toFloat(), centerY, midX.toFloat(), centerY, paintLine)

            // Garis koreksi di ujung garis kuning tengah prediksi
            canvas.drawLine(midX.toFloat(), centerY - 10, midX.toFloat(), centerY + 10, paintIndicator)

            // Tambahan: Garis vertikal hijau di titik prediksi kiri dan kanan
            if (laneLines.size == 2) {
                val leftX = ((laneLines[0].first.x / imageWidth) * width).toFloat()
                val rightX = ((laneLines[1].first.x / imageWidth) * width).toFloat()
                listOf(leftX, rightX, midX.toFloat()).forEach { x ->
                    canvas.drawLine(x, centerY - 14, x, centerY + 14, paintIndicator)
                }
            }

            // Teks arah belok
            val textWidth = paintText.measureText(steeringDirection!!)
            canvas.drawText(steeringDirection!!, (width - textWidth) / 2, centerY - 40, paintText)
        } else if (now - lastSteeringUpdateTime >= steeringTimeoutMs) {
            // Jika timeout, clear steering info agar tidak digambar lagi
            clearLaneInfo()
        }


        // Garis vertikal dari bawah ke tengah (ini harus selalu ada)
        canvas.drawLine(centerX.toFloat(), height.toFloat(), centerX.toFloat(), centerY, paintLine)

        // Garis batas belok kiri/kanan (ini harus selalu ada)
        listOf(centerX - 100, centerX + 100).forEach { x ->
            canvas.drawLine(x.toFloat(), centerY - 10, x.toFloat(), centerY + 10, paintBoundary)
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    fun setLaneLines(lines: List<Pair<Point, Point>>) {
        laneLines = lines
        // Invalidate akan dipanggil oleh setSteeringInfo jika ada, atau di Main activity.
        // Jika tidak ada steering info, tetap perlu invalidate untuk update garis jalur.
        invalidate()
    }

    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    fun setSteeringInfo(midX: Int? = null, direction: String? = null) {
        if (midX != null && direction != null) {
            laneMidX = midX
            steeringDirection = direction
            lastSteeringUpdateTime = System.currentTimeMillis()
        } else {
            // Jika tidak ada info steering baru, cek timeout
            val now = System.currentTimeMillis()
            if (now - lastSteeringUpdateTime >= steeringTimeoutMs) {
                // Hapus steering info jika sudah melewati timeout
                clearLaneInfo()
            }
        }
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}