package com.aprilarn.carvis

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object LaneDetector {

    private var previousLines: List<Line>? = null
    private var lastDetectionTime: Long = 0L
    private const val MAX_NO_DETECTION_DURATION = 1000 // Durasi maksimum (dalam milidetik) tanpa deteksi sebelum previousLines di-reset menjadi null

    data class Line(val start: Point, val end: Point)

    // Fungsi utama untuk mendeteksi garis jalur pada bitmap input
    fun detectLaneWithLines(bitmap: Bitmap): List<Pair<Point, Point>> {

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val edges = detectEdges(mat)
        val roi = applyROI(edges)
        val lines = detectLines(roi)
        val averaged = averageSlopeIntercept(roi.size(), lines)

        val currentTime = System.currentTimeMillis()
        if (averaged != null) {
            previousLines = lerpLines(previousLines, averaged)
            lastDetectionTime = currentTime
        } else if (currentTime - lastDetectionTime > MAX_NO_DETECTION_DURATION) {
            previousLines = null
        }

        return previousLines?.map { it.start to it.end } ?: emptyList()

    }

    // Fungsi untuk mendeteksi tepi menggunakan algoritma Canny
    private fun detectEdges(frame: Mat): Mat {

        val gray = Mat()
        val blur = Mat()
        val edges = Mat()

        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0) // Menggunakan Gaussian Blur untuk mengurangi noise
        Imgproc.Canny(blur, edges, 50.0, 150.0) // Menggunakan Canny Edge Detection untuk mendeteksi tepi
        return edges

    }

    // Fungsi untuk menerapkan Region of Interest (ROI) pada gambar
    private fun applyROI(image: Mat): Mat {

        val height = image.rows()
        val width = image.cols()
        val mask = Mat.zeros(image.size(), CvType.CV_8UC1)

//        val polygon = MatOfPoint(
//            Point(0.0, height.toDouble()),                  // Left bottom corner
//            Point(width.toDouble(), height.toDouble()),     // Right bottom corner
//            Point(0.575 * width, 0.55 * height),            // Right top corner
//            Point(0.425 * width, 0.55 * height)             // Left top corner
//        )

        val polygon = MatOfPoint(

            Point(0.0, height.toDouble()),                    // 1: Bottom-Left
            Point(width.toDouble(), height.toDouble()),       // 2: Bottom-Right
            Point(width.toDouble(), 0.87 * height),           // 3: Mid-Right
            Point(0.61 * width, 0.55 * height),               // 4: Top-Right
            Point(0.39 * width, 0.55 * height) ,              // 5: Top-Left
            Point(0.0, 0.87 * height)                         // 6: Mid-Left

        )

        Imgproc.fillPoly(mask, listOf(polygon), Scalar(255.0)) // Mengisi area polygon pada mask dengan nilai 255 (putih)
        val masked = Mat() // Mat untuk menyimpan hasil masking
        Core.bitwise_and(image, mask, masked) // Menggunakan bitwise_and untuk menerapkan ROI pada gambar
        return masked

    }

    // Fungsi untuk mendeteksi garis menggunakan Probabilistic Hough Line Transform (HoughLinesP)
    private fun detectLines(image: Mat): List<Line> {

        val lines = Mat()
        // theta, treshold, minLineLength, maxLineGap
        Imgproc.HoughLinesP(image, lines, 1.0, Math.PI / 180, 78, 40.0, 80.0)

        val result = mutableListOf<Line>()
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            result.add(Line(Point(l[0], l[1]), Point(l[2], l[3])))
        }
        return result

    }

    // Fungsi untuk merata-ratakan kemiringan (slope) dan perpotongan (intercept) garis
    // untuk mengidentifikasi garis jalur kiri dan kanan
    private fun averageSlopeIntercept(size: Size, lines: List<Line>): List<Line>? {

        val left = mutableListOf<Pair<Double, Double>>()
        val right = mutableListOf<Pair<Double, Double>>()
        val centerX = size.width / 2

        // Mengelompokkan garis-garis yang terdeteksi menjadi garis kiri atau kanan
        for (line in lines) {
            val x1 = line.start.x
            val y1 = line.start.y
            val x2 = line.end.x
            val y2 = line.end.y

            if (x2 - x1 == 0.0) continue      // Menghindari pembagian dengan nol
            val slope = (y2 - y1) / (x2 - x1) // Menghitung kemiringan garis
            val intercept = y1 - slope * x1   // Menghitung perpotongan garis dengan sumbu y

            // Mengidentifikasi garis kiri: slope negatif dan berada di sisi kiri centerX
            if (slope < -0.5 && x1 < centerX && x2 < centerX) {
                left.add(slope to intercept)
            }
            // Mengidentifikasi garis kanan: slope positif dan berada di sisi kanan centerX
            else if (slope > 0.5 && x1 > centerX && x2 > centerX) {
                right.add(slope to intercept)
            }

        }

        if (left.isEmpty() || right.isEmpty()) return null

        val leftAvg = averageParams(left)
        val rightAvg = averageParams(right)

        return listOf(
            makeCoordinates(size, leftAvg),
            makeCoordinates(size, rightAvg)
        )

    }

    // Fungsi pembantu untuk merata-ratakan daftar pasangan (slope, intercept)
    private fun averageParams(params: List<Pair<Double, Double>>): Pair<Double, Double> {

        val slope = params.map { it.first }.average()
        val intercept = params.map { it.second }.average()
        return slope to intercept

    }

    // Fungsi untuk membuat koordinat titik awal dan akhir sebuah garis dari slope dan intercept
    private fun makeCoordinates(size: Size, params: Pair<Double, Double>): Line {

        val (slope, intercept) = params
        val y1 = size.height * 0.85
        val y2 = size.height * 0.65
        val x1 = (y1 - intercept) / slope
        val x2 = (y2 - intercept) / slope
        return Line(Point(x1, y1), Point(x2, y2))

    }

    // Fungsi untuk melakukan interpolasi linear (Lerp) antara garis-garis lama dan baru
    // Digunakan untuk menghaluskan pergerakan garis
    private fun lerpLines(oldLines: List<Line>?, newLines: List<Line>, alpha: Double = 0.5): List<Line> {

        if (oldLines == null) return newLines
        return oldLines.zip(newLines).map { (old, new) ->
            Line(
                lerpPoint(old.start, new.start, alpha),
                lerpPoint(old.end, new.end, alpha)
            )
        }

    }

    // Fungsi pembantu untuk melakukan interpolasi linear antara dua titik
    private fun lerpPoint(p1: Point, p2: Point, alpha: Double): Point {

        return Point(
            (1 - alpha) * p1.x + alpha * p2.x,
            (1 - alpha) * p1.y + alpha * p2.y
        )

    }

}

