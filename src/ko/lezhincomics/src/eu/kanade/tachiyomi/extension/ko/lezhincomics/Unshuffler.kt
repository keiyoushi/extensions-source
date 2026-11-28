package eu.kanade.tachiyomi.extension.ko.lezhincomics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

// The reader draws contents cuts from a 5x5 grid of tiles shuffled by the cut's shuffle key
object Unshuffler {

    private const val GRID = 5

    fun unshuffle(source: Bitmap, seed: Long): Bitmap {
        val tileWidth = source.width / GRID
        val tileHeight = source.height / GRID
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Right and bottom remainder strips stay in place
        canvas.drawBitmap(source, 0f, 0f, null)
        order(seed).forEachIndexed { position, tile ->
            val sourceX = tile % GRID * tileWidth
            val sourceY = tile / GRID * tileHeight
            val destinationX = position % GRID * tileWidth
            val destinationY = position / GRID * tileHeight
            canvas.drawBitmap(
                source,
                Rect(sourceX, sourceY, sourceX + tileWidth, sourceY + tileHeight),
                Rect(destinationX, destinationY, destinationX + tileWidth, destinationY + tileHeight),
                null,
            )
        }
        return result
    }

    // xorshift64 (12, 25, 27) driving a Fisher-Yates whose swap partner is drawn from the whole grid every step
    private fun order(seed: Long): IntArray {
        val count = GRID * GRID
        val order = IntArray(count) { it }
        var state = seed.toULong()
        for (index in 0 until count) {
            state = state xor (state shr 12)
            state = state xor (state shl 25)
            state = state xor (state shr 27)
            val swap = ((state shr 32) % count.toULong()).toInt()
            order[swap] = order[index].also { order[index] = order[swap] }
        }
        return order
    }
}
