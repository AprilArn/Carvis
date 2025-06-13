package com.aprilarn.carvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        results = listOf()
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
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

        results.forEach { result ->
            val left = result.x1 * width
            val top = result.y1 * height
            val right = result.x2 * width
            val bottom = result.y2 * height

            // Gambar bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = result.clsName
            val padding = BOUNDING_RECT_TEXT_PADDING

            // Hitung lebar dan tinggi teks
            val textWidth = textPaint.measureText(text)
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val textHeight = bounds.height()

            // Default posisi background teks: di atas bounding box
            var bgLeft = left
            var bgTop = top - textHeight - 2 * padding
            var bgRight = bgLeft + textWidth + 2 * padding
            var bgBottom = top

            // Koreksi kanan
            if (bgRight > width) {
                val overflow = bgRight - width
                bgLeft -= overflow
                bgRight = width.toFloat()
            }

            // Koreksi kiri
            if (bgLeft < 0f) {
                bgLeft = 0f
                bgRight = textWidth + 2 * padding
            }

            // Koreksi atas: geser turun sedikit supaya tidak keluar layar, tapi tetap di atas bounding box
            if (bgTop < 0f) {
                val delta = 0f - bgTop
                bgTop += delta
                bgBottom += delta
            }

            // Hitung posisi teks (baseline)
            val textX = bgLeft + padding
            val textY = bgBottom - padding - bounds.bottom

            // Gambar background dan teks
            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBackgroundPaint)
            canvas.drawText(text, textX, textY, textPaint)
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}