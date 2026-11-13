package eu.kanade.tachiyomi.multisrc.catharsis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

// Tile boundaries, with the order the reader shuffled the columns and rows into
class TilePlan(val cols: IntArray, val rows: IntArray, val colOrder: IntArray, val rowOrder: IntArray)

// Undoes the reader's 8x8 row/column shuffle, planned for the image without gutters; shuffled tiles may sit [gutter] px apart
fun descramble(bitmap: Bitmap, gutter: Int = 0, plan: (width: Int, height: Int) -> TilePlan): Bitmap {
    // The reader drops a gutter that would leave less than 200 px
    val tileGutter = gutter.takeIf { minOf(bitmap.width, bitmap.height) - 2 * GRID * it >= MIN_GUTTERED_SIZE } ?: 0
    val outputWidth = bitmap.width - 2 * GRID * tileGutter
    val outputHeight = bitmap.height - 2 * GRID * tileGutter
    val tiles = plan(outputWidth, outputHeight)
    val srcCols = offsets(tiles.cols, tiles.colOrder)
    val srcRows = offsets(tiles.rows, tiles.rowOrder)

    val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { isFilterBitmap = false }
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            val width = tiles.cols[col + 1] - tiles.cols[col]
            val height = tiles.rows[row + 1] - tiles.rows[row]
            val colPosition = tiles.colOrder.indexOf(col)
            val rowPosition = tiles.rowOrder.indexOf(row)
            // Each shuffled strip has a gutter on both sides
            val srcX = srcCols[colPosition] + tileGutter * (2 * colPosition + 1)
            val srcY = srcRows[rowPosition] + tileGutter * (2 * rowPosition + 1)
            canvas.drawBitmap(
                bitmap,
                Rect(srcX, srcY, srcX + width, srcY + height),
                Rect(tiles.cols[col], tiles.rows[row], tiles.cols[col] + width, tiles.rows[row] + height),
                paint,
            )
        }
    }
    return output
}

private fun offsets(bounds: IntArray, order: IntArray): IntArray {
    val offsets = IntArray(GRID + 1)
    for (i in 0 until GRID) {
        offsets[i + 1] = offsets[i] + bounds[order[i] + 1] - bounds[order[i]]
    }
    return offsets
}

private const val GRID = 8
private const val MIN_GUTTERED_SIZE = 200
