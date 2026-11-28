package com.sonder.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a Chapter recap as a private PNG card. The card contains only what
 * the user chose to share (chapter title, dates, counts and recurring threads)
 * and never a Moment body. The file is written to the app cache and handed to
 * the system share sheet through a FileProvider.
 */
object RecapCardRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun render(context: Context, title: String, recapText: String): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 241, 232))

        val accent = Color.rgb(138, 122, 102)
        val ink = Color.rgb(44, 41, 37)
        val soft = Color.rgb(107, 100, 92)

        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 249, 243)
        }
        canvas.drawRoundRect(
            RectF(56f, 56f, WIDTH - 56f, HEIGHT - 56f),
            48f,
            48f,
            card
        )
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.rgb(227, 216, 200)
        }
        canvas.drawRoundRect(
            RectF(56f, 56f, WIDTH - 56f, HEIGHT - 56f),
            48f,
            48f,
            border
        )

        var y = 170f
        val eyebrow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 32f
            letterSpacing = 0.18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("A CHAPTER KEPT QUIETLY", 110f, y, eyebrow)
        y += 92f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 82f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        y = drawWrapped(canvas, title, titlePaint, 110f, y, WIDTH - 220f, 92f)
        y += 62f

        val lines = recapText.lines()
            .dropWhile { it.isBlank() || it.startsWith("Chapter:") }
            .dropWhile { it.isBlank() }
            .filter { it.isNotBlank() }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = soft
            textSize = 40f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        lines.forEach { line ->
            val isStat = line.startsWith("Weather:") || line.startsWith("Recurring")
            val paint = if (isStat) Paint(body).apply { color = ink; textSize = 42f } else body
            y = drawWrapped(canvas, line, paint, 110f, y, WIDTH - 220f, 60f)
            y += if (isStat) 18f else 12f
            if (y > HEIGHT - 260f) return@forEach
        }

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 30f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("SONDER · ON THIS DEVICE ONLY", 110f, HEIGHT - 130f, footer)

        val dir = File(context.cacheDir, "recap_cards").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 86_400_000L) it.delete() }
        val file = File(dir, "chapter-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float
    ): Float {
        var remaining = text.trim()
        var y = startY
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) break
            var cut = count
            val space = remaining.lastIndexOf(' ', cut)
            if (space > 0 && count < remaining.length) cut = space
            val line = remaining.substring(0, cut).trimEnd()
            canvas.drawText(line, x, y, paint)
            y += lineHeight
            remaining = remaining.substring(cut).trimStart()
        }
        return y
    }
}
