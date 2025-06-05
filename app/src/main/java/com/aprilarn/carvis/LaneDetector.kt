//package com.aprilarn.carvis
//
//import android.graphics.Bitmap
//import org.opencv.android.Utils
//import org.opencv.core.*
//import org.opencv.imgproc.Imgproc
//import java.util.*
//
//object LaneDetector {
//
//    private var previousLines: Array<Line>? = null
//    private var lastDetectionTime: Long = 0
//
//    private const val MAX_NO_DETECTION_DURATION = 2000 // ms
//    private const val SMOOTH_ALPHA = 0.1
//
//    data class Line(val x1: Int, val y1: Int, val x2: Int, val y2: Int)
//
//    fun process(bitmap: Bitmap): Bitmap {
//        val mat = Mat()
//        Utils.bitmapToMat(bitmap, mat)
//
//        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
//        val edges = detectEdges(mat)
//        val (roi, polygon) = regionOfInterest(edges)
//        val lines = detectLines(roi)
//        val averaged = averageSlopeIntercept(mat, lines)
//
//        val currentTime = System.currentTimeMillis()
//        if (averaged != null) {
//            previousLines = if (previousLines == null) averaged else lerpLines(previousLines!!, averaged)
//            lastDetectionTime = currentTime
//        } else {
//            if (currentTime - lastDetectionTime > MAX_NO_DETECTION_DURATION) {
//                previousLines = null
//            }
//        }
//
//        val output = drawOverlay(mat, polygon, previousLines)
//        val result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888)
//        Utils.matToBitmap(output, result)
//        return result
//    }
//
//    private fun detectEdges(image: Mat): Mat {
//        val gray = Mat()
//        Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGB2GRAY)
//        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
//        val edges = Mat()
//        Imgproc.Canny(gray, edges, 50.0, 150.0)
//        return edges
//    }
//
//    private fun regionOfInterest(image: Mat): Pair<Mat, MatOfPoint> {
//        val mask = Mat.zeros(image.size(), CvType.CV_8UC1)
//        val height = image.height()
//        val width = image.width()
//        val polygon = MatOfPoint(
//            Point(0.0, height.toDouble()),
//            Point(width.toDouble(), height.toDouble()),
//            Point(width * 0.575, height * 0.55),
//            Point(width * 0.425, height * 0.55)
//        )
//        Imgproc.fillPoly(mask, listOf(polygon), Scalar(255.0))
//        val masked = Mat()
//        Core.bitwise_and(image, mask, masked)
//        return masked to polygon
//    }
//
//    private fun detectLines(image: Mat): MatOfLines {
//        val lines = MatOfLines()
//        Imgproc.HoughLinesP(image, lines, 1.0, Math.PI / 180, 78, 40.0, 80.0)
//        return lines
//    }
//
//    private fun averageSlopeIntercept(image: Mat, lines: MatOfLines): Array<Line>? {
//        val left = mutableListOf<Pair<Double, Double>>()
//        val right = mutableListOf<Pair<Double, Double>>()
//        val width = image.width()
//        val centerX = width / 2
//
//        for (line in lines.toArray()) {
//            val (x1, y1, x2, y2) = listOf(line[0].toInt(), line[1].toInt(), line[2].toInt(), line[3].toInt())
//            val params = polyfit(x1, x2, y1, y2) ?: continue
//            val (slope, intercept) = params
//
//            if (slope < -0.5 && x1 < centerX && x2 < centerX) {
//                left.add(slope to intercept)
//            } else if (slope > 0.5 && x1 > centerX && x2 > centerX) {
//                right.add(slope to intercept)
//            }
//        }
//
//        if (left.isEmpty() || right.isEmpty()) return null
//
//        val leftAvg = avgLine(left)
//        val rightAvg = avgLine(right)
//
//        return arrayOf(
//            makeCoordinates(image, leftAvg),
//            makeCoordinates(image, rightAvg)
//        )
//    }
//
//    private fun polyfit(x1: Int, x2: Int, y1: Int, y2: Int): Pair<Double, Double>? {
//        return try {
//            val m = (y2 - y1).toDouble() / (x2 - x1)
//            val b = y1 - m * x1
//            m to b
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    private fun avgLine(lines: List<Pair<Double, Double>>): Pair<Double, Double> {
//        val avgSlope = lines.map { it.first }.average()
//        val avgIntercept = lines.map { it.second }.average()
//        return avgSlope to avgIntercept
//    }
//
//    private fun makeCoordinates(image: Mat, lineParams: Pair<Double, Double>): Line {
//        var (slope, intercept) = lineParams
//        val y1 = (image.height() * 0.85).toInt()
//        val y2 = (image.height() * 0.65).toInt()
//        if (slope == 0.0) slope = 0.1
//        val x1 = ((y1 - intercept) / slope).toInt()
//        val x2 = ((y2 - intercept) / slope).toInt()
//        return Line(x1, y1, x2, y2)
//    }
//
//    private fun lerpLines(oldLines: Array<Line>, newLines: Array<Line>): Array<Line> {
//        return oldLines.zip(newLines).map { (old, new) ->
//            val x1 = lerp(old.x1, new.x1)
//            val y1 = lerp(old.y1, new.y1)
//            val x2 = lerp(old.x2, new.x2)
//            val y2 = lerp(old.y2, new.y2)
//            Line(x1, y1, x2, y2)
//        }.toTypedArray()
//    }
//
//    private fun lerp(a: Int, b: Int): Int = (a * (1 - SMOOTH_ALPHA) + b * SMOOTH_ALPHA).toInt()
//
//    private fun drawOverlay(base: Mat, polygon: MatOfPoint, lines: Array<Line>?): Mat {
//        val overlay = base.clone()
//        Imgproc.fillPoly(overlay, listOf(polygon), Scalar(0.0, 0.0, 0.0), Imgproc.LINE_8)
//        Core.addWeighted(overlay, 0.3, base, 0.7, 0.0, base)
//
//        lines?.forEach {
//            Imgproc.line(base, Point(it.x1.toDouble(), it.y1.toDouble()), Point(it.x2.toDouble(), it.y2.toDouble()), Scalar(0.0, 255.0, 0.0), 2)
//        }
//
//        return base
//    }
//}
