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

package com.aprilarn.carvis

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.opencv.core.Point // Pastikan ini diimpor jika Point dari OpenCV

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var laneLines = listOf<Pair<Point, Point>>()
    private var imageWidth = 1
    private var imageHeight = 1

    private var steeringDirection: String? = null
    private var laneMidX: Int? = null
    private var lastSteeringUpdateTime: Long = 0L
    private val steeringTimeoutMs = 150L // 0.15 detik (sesuaikan jika ingin sama dengan LaneDetector)

    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var bounds = Rect()

    // Tambahkan Paint baru untuk ROI
    private var roiPaint = Paint()

    init {
        initPaints()
    }

    fun clear() {
        clearBoundingBoxes()
        clearLaneInfo()
        invalidate()
        initPaints()
    }

    fun clearBoundingBoxes() {
        results = listOf()
    }

    fun clearLaneInfo() {
        laneLines = listOf()
        steeringDirection = null
        laneMidX = null
        lastSteeringUpdateTime = 0L
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

        // Inisialisasi roiPaint
        roiPaint.color = Color.argb(60, 0, 0, 0) // Hitam transparan (alpha 100 dari 255)
        roiPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // --- 1. Gambar ROI (Region of Interest) ---
        drawRoi(canvas)

        // --- 2. Gambar bounding box YOLO ---
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

        // --- 3. Gambar lane Prediction ---
        val lanePaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }

        laneLines.forEach { (start, end) ->
            // Skala koordinat dari ukuran gambar asli ke ukuran OverlayView
            val sx = ((start.x / imageWidth) * width).toFloat()
            val sy = ((start.y / imageHeight) * height).toFloat()
            val ex = ((end.x / imageWidth) * width).toFloat()
            val ey = ((end.y / imageHeight) * height).toFloat()

            canvas.drawLine(sx, sy, ex, ey, lanePaint)
        }

        // --- 4. Steering Advice ---
        val centerX = width / 2
        val centerY = (height * 0.84f)

        val paintVerticalHorizontalLine = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

//        val paintHorizontalLine = Paint().apply {
//            color = Color.CYAN
//            strokeWidth = 4f
//            style = Paint.Style.STROKE
//        }

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

        val now = System.currentTimeMillis()
        if (laneMidX != null && steeringDirection != null && now - lastSteeringUpdateTime < steeringTimeoutMs) {
            val midX = laneMidX!!

            // Draw horizontal line to center indicator
            canvas.drawLine(centerX.toFloat(), centerY, midX.toFloat(), centerY, paintVerticalHorizontalLine)

            // Draw center support indicator
            //canvas.drawLine(midX.toFloat(), centerY - 10, midX.toFloat(), centerY + 10, paintIndicator)

            // Draw 3 indicator
            if (laneLines.size == 2) {
                val leftX = ((laneLines[0].first.x / imageWidth) * width).toFloat()
                val rightX = ((laneLines[1].first.x / imageWidth) * width).toFloat()

                // leftX=left indicator, rightX=right indicator, midX=center indicator
                listOf(leftX, rightX, midX.toFloat()).forEach { x ->
                    canvas.drawLine(x, centerY - 14, x, centerY + 14, paintIndicator)
                }
            }

            // Show text direction
            val textWidth = paintText.measureText(steeringDirection!!)
            canvas.drawText(steeringDirection!!, (width - textWidth) / 2, centerY - 40, paintText)
        } else if (now - lastSteeringUpdateTime >= steeringTimeoutMs) {
            clearLaneInfo()
        }

        // Draw Draw center vertical line
        canvas.drawLine(centerX.toFloat(), height.toFloat(), centerX.toFloat(), centerY, paintVerticalHorizontalLine)

        // Draw boundary line
        listOf(centerX - 85, centerX + 85).forEach { x ->
            canvas.drawLine(x.toFloat(), centerY - 10, x.toFloat(), centerY + 10, paintBoundary)
        }
    }

    // Fungsi baru untuk menggambar ROI
    private fun drawRoi(canvas: Canvas) {
        val path = Path()

        // Koordinat ROI berdasarkan rasio yang sama dengan LaneDetector.kt
        // Ingat, width dan height di sini adalah dimensi OverlayView
        val currentWidth = width.toFloat()
        val currentHeight = height.toFloat()

        // Poin-poin ROI:
        // Point(0.0, height.toDouble()),                  // Left bottom corner
        // Point(width.toDouble(), height.toDouble()),     // Right bottom corner
        // Point(0.575 * width, 0.55 * height),            // Right top corner
        // Point(0.425 * width, 0.55 * height)             // Left top corner

        path.moveTo(0f, currentHeight)                                // 1: Bottom-Left
        path.lineTo(currentWidth, currentHeight)                      // 2: Bottom-Right
        path.lineTo(currentWidth, 0.87f * currentHeight)              // 3: Mid-Right
        path.lineTo(0.61f * currentWidth, 0.55f * currentHeight)      // 4: Top-Right
        path.lineTo(0.39f * currentWidth, 0.55f * currentHeight)      // 5: Top-Left
        path.lineTo(0f, 0.87f * currentHeight)

        canvas.drawPath(path, roiPaint)
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    fun setLaneLines(lines: List<Pair<Point, Point>>) {
        laneLines = lines
        if (laneLines.isEmpty()) {
            clearLaneInfo()
        }
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
            val now = System.currentTimeMillis()
            if (now - lastSteeringUpdateTime >= steeringTimeoutMs) {
                clearLaneInfo()
            }
        }
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}