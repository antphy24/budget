package com.example.budget.scan

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads text from an image with ML Kit's on-device recognizer (the model ships inside the app;
 * images and text never leave the phone). Returns the text as visual rows: pieces that sit on
 * the same line are joined left to right, so "SABUN LIFEBUOY" and "9,900" become one row.
 */
@Singleton
class TextReader @Inject constructor(@ApplicationContext private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Blocking; call from a background thread. */
    fun readRows(uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val result = Tasks.await(recognizer.process(image))

        data class Piece(val text: String, val left: Int, val top: Int, val bottom: Int) {
            val center get() = (top + bottom) / 2
            val height get() = (bottom - top).coerceAtLeast(1)
        }
        val pieces = result.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
            val box = l.boundingBox ?: return@mapNotNull null
            Piece(l.text.trim(), box.left, box.top, box.bottom)
        }.filter { it.text.isNotEmpty() }.sortedBy { it.center }

        // Group pieces whose vertical centres are within half a line height of the row's
        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.center }.average()
                val rowHeight = row.map { it.height }.average()
                if (kotlin.math.abs(p.center - rowCenter) < rowHeight * 0.5) {
                    row += p
                    continue
                }
            }
            rows += mutableListOf(p)
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
