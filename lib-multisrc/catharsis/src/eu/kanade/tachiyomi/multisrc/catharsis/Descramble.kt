package eu.kanade.tachiyomi.multisrc.catharsis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

// Tile boundaries, with the order the reader shuffled the columns and rows into
class TilePlan(val cols: IntArray, val rows: IntArray, val colOrder: IntArray, val rowOrder: IntArray)

// The reader's 8x8 plan drawn from [random]
fun tilePlan(random: () -> Double, width: Int, height: Int): TilePlan {
    val cols = boundaries(random, width)
    val rows = boundaries(random, height)
    return TilePlan(cols, rows, permutation(random), permutation(random))
}

// Undoes the reader's 8x8 row/column shuffle, planned for the image without gutters; shuffled tiles may sit [gutter] px apart
fun descramble(bitmap: Bitmap, gutter: Int = 0, plan: (width: Int, height: Int) -> TilePlan): Bitmap {
    // The reader drops a gutter that would leave less than 200 px
    val tileGutter = gutter.takeIf { minOf(bitmap.width, bitmap.height) - 2 * GRID * it >= MIN_GUTTERED_SIZE } ?: 0
    val outputWidth = bitmap.width - 2 * GRID * tileGutter
    val outputHeight = bitmap.height - 2 * GRID * tileGutter
    val tiles = plan(outputWidth, outputHeight)
    val cols = tiles.cols
    val rows = tiles.rows
    val colOrder = tiles.colOrder
    val rowOrder = tiles.rowOrder
    val srcCols = offsets(cols, colOrder)
    val srcRows = offsets(rows, rowOrder)

    val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { isFilterBitmap = false }
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            val width = cols[col + 1] - cols[col]
            val height = rows[row + 1] - rows[row]
            val colPosition = colOrder.indexOf(col)
            val rowPosition = rowOrder.indexOf(row)
            // Each shuffled strip has a gutter on both sides
            val srcX = srcCols[colPosition] + tileGutter * (2 * colPosition + 1)
            val srcY = srcRows[rowPosition] + tileGutter * (2 * rowPosition + 1)
            canvas.drawBitmap(
                bitmap,
                Rect(srcX, srcY, srcX + width, srcY + height),
                Rect(cols[col], rows[row], cols[col] + width, rows[row] + height),
                paint,
            )
        }
    }
    return output
}

private fun boundaries(random: () -> Double, total: Int): IntArray {
    val bounds = IntArray(GRID + 1)
    bounds[GRID] = total
    val base = total / GRID
    var position = 0
    for (i in 0 until GRID - 1) {
        val max = total - position - (GRID - 1 - i) * MIN_TILE
        position += (base + ((random() * 0.7 - 0.35) * base).roundToInt()).coerceIn(MIN_TILE, maxOf(MIN_TILE, max))
        bounds[i + 1] = position
    }
    return bounds
}

private fun permutation(random: () -> Double): IntArray {
    val order = IntArray(GRID) { it }
    for (i in GRID - 1 downTo 1) {
        val j = (random() * (i + 1)).toInt()
        order[i] = order[j].also { order[j] = order[i] }
    }
    return order
}

private fun offsets(bounds: IntArray, order: IntArray): IntArray {
    val offsets = IntArray(GRID + 1)
    for (i in 0 until GRID) {
        offsets[i + 1] = offsets[i] + bounds[order[i] + 1] - bounds[order[i]]
    }
    return offsets
}

private const val GRID = 8
private const val MIN_TILE = 16
private const val MIN_GUTTERED_SIZE = 200
