package eu.kanade.tachiyomi.extension.ja.soraraw

import keiyoushi.utils.readIntBigEndian
import java.security.MessageDigest

// Port of soraraw.wasm `unscramble`, the site reader's "canva2" mode
object Canva2Descrambler {

    private const val GRID = 8

    // Digest embedded in the wasm
    private const val SALT = "6a0248ad1ca4208275aed64d336e81595ecb149422a8e621f70e23b9f01b9c1c"

    private class Tile(val x: Int, val y: Int, val w: Int, val h: Int)

    fun descramble(pixels: IntArray, width: Int, height: Int, chapterId: String): IntArray {
        val h1 = sha256(chapterId + SALT).toHexString()
        val h2 = sha256("$h1:nxn:$GRID:${width}x$height")
        val rng = Mulberry32(h2.readIntBigEndian(0))

        val tileW = width / GRID
        val tileH = height / GRID
        val extraW = width - tileW * GRID
        val extraH = height - tileH * GRID
        val tiles = buildList {
            var y = 0
            for (r in 0 until GRID) {
                val h = if (r < extraH) tileH + 1 else tileH
                var x = 0
                for (c in 0 until GRID) {
                    val w = if (c < extraW) tileW + 1 else tileW
                    add(Tile(x, y, w, h))
                    x += w
                }
                y += h
            }
        }

        val classes = LinkedHashMap<Pair<Int, Int>, MutableList<Int>>()
        tiles.forEachIndexed { i, t -> classes.getOrPut(t.w to t.h) { mutableListOf() }.add(i) }

        val sources = HashMap<Pair<Int, Int>, List<Int>>()
        val rotations = HashMap<Pair<Int, Int>, List<Int>>()
        for ((size, members) in classes) {
            val order = members.toMutableList()
            for (count in order.size downTo 2) {
                val j = rng.nextInt(count)
                order[count - 1] = order[j].also { order[j] = order[count - 1] }
            }
            sources[size] = order
            rotations[size] = List(order.size) {
                when {
                    size.first == size.second -> rng.nextInt(4)
                    rng.nextDouble() < 0.5 -> 0
                    else -> 2
                }
            }
        }

        val out = IntArray(pixels.size)
        val position = HashMap<Pair<Int, Int>, Int>()
        tiles.forEach { dst ->
            val size = dst.w to dst.h
            val pos = position.getOrDefault(size, 0).also { position[size] = it + 1 }
            val src = tiles[sources.getValue(size)[pos]]
            draw(pixels, width, src, out, dst, rotations.getValue(size)[pos])
        }
        return out
    }

    private fun draw(pixels: IntArray, width: Int, src: Tile, out: IntArray, dst: Tile, rotation: Int) {
        val w = dst.w
        val h = dst.h
        for (r in 0 until h) {
            for (c in 0 until w) {
                val (y, x) = when (rotation) {
                    1 -> c to h - 1 - r
                    2 -> h - 1 - r to w - 1 - c
                    3 -> w - 1 - c to r
                    else -> r to c
                }
                out[(dst.y + y) * width + dst.x + x] = pixels[(src.y + r) * width + src.x + c]
            }
        }
    }

    private fun sha256(input: String) = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())

    private class Mulberry32(private var state: Int) {
        fun nextDouble(): Double {
            state += 0x6D2B79F5
            var t = (state xor (state ushr 15)) * (state or 1)
            t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
            return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
        }

        fun nextInt(bound: Int) = (nextDouble() * bound).toInt()
    }
}
