package eu.kanade.tachiyomi.extension.ja.soraraw

import keiyoushi.utils.SeedRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// Port of the site reader's "canva" mode
object CanvaDescrambler {

    private const val TARGET = 222
    private const val SEG_MAX = 259
    private const val SEG_MIN = 185
    private const val WHITE = -1

    private class Tile(val x: Int, val y: Int, val w: Int, val h: Int)

    private class Transform(val rot: Int, val flipH: Boolean, val flipV: Boolean)

    private class Move(val src: Int, val transform: Transform)

    fun descramble(pixels: IntArray, width: Int, height: Int, chapterId: String, token: String): IntArray {
        val key = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(token.toByteArray(), "HmacSHA256"))
            doFinal(chapterId.toByteArray()).toHexString()
        }

        val splitRng = SeedRandom(key)
        val cols = split(width, splitRng)
        val rows = split(height, splitRng)
        val tiles = buildList {
            var y = 0
            for (h in rows) {
                var x = 0
                for (w in cols) {
                    add(Tile(x, y, w, h))
                    x += w
                }
                y += h
            }
        }

        val classes = LinkedHashMap<String, MutableList<Int>>()
        tiles.forEachIndexed { i, t -> classes.getOrPut("${t.w}x${t.h}") { mutableListOf() }.add(i) }

        val moves = HashMap<Int, Move>()
        val done = HashSet<String>()
        for ((a, members) in classes) {
            if (a in done) continue
            done.add(a)
            val (aw, ah) = a.split('x').map(String::toInt)
            val square = aw == ah
            val b = "${ah}x$aw"
            val partner = classes[b]?.takeIf { b != a }
            if (partner == null) {
                val order = members.toMutableList()
                shuffle(order, SeedRandom("$key|perm|$a"))
                members.indices.forEach { i ->
                    moves[members[i]] = Move(order[i], transform(SeedRandom("$key|tf|self|$a|$i"), false, square))
                }
                continue
            }
            done.add(b)
            val pairRng = SeedRandom("$key|pair|$a|$b")
            val sa = members.toMutableList().also { shuffle(it, pairRng) }
            val sb = partner.toMutableList().also { shuffle(it, pairRng) }
            val m = min(sa.size, sb.size)
            for (i in 0 until m) moves[sa[i]] = Move(sb[i], transform(SeedRandom("$key|tf|AtoB|$a|$i"), true, false))
            for (i in 0 until m) moves[sb[i]] = Move(sa[i], transform(SeedRandom("$key|tf|BtoA|$b|$i"), true, false))
            restMoves(sa.drop(m), "$key|perm|A|$a", "$key|tf|A|$a|rest", square, moves)
            restMoves(sb.drop(m), "$key|perm|B|$b", "$key|tf|B|$b|rest", square, moves)
        }

        val out = IntArray(width * height) { WHITE }
        for ((slot, move) in moves) {
            val dst = tiles[slot]
            val src = tiles[move.src]
            draw(pixels, width, src, out, dst, move.transform)
        }
        return out
    }

    private fun restMoves(rest: List<Int>, permSeed: String, tfSeed: String, square: Boolean, moves: HashMap<Int, Move>) {
        if (rest.isEmpty()) return
        val order = rest.toMutableList()
        shuffle(order, SeedRandom(permSeed))
        rest.indices.forEach { i ->
            moves[rest[i]] = Move(order[i], transform(SeedRandom("$tfSeed|$i"), false, square))
        }
    }

    private fun split(total: Int, rng: SeedRandom): List<Int> {
        var count = maxOf(1, (total / TARGET.toDouble()).roundToInt())
        while (total / count > SEG_MAX) count++
        while (total / count < SEG_MIN && count > 1) count--
        val base = total / count
        val rem = total - base * count
        val sizes = MutableList(count) { if (it < rem) base + 1 else base }
        shuffle(sizes, rng)
        return sizes
    }

    private fun shuffle(list: MutableList<Int>, rng: SeedRandom) {
        for (i in list.size - 1 downTo 1) {
            val j = floor(rng.nextDouble() * (i + 1)).toInt()
            list[i] = list[j].also { list[j] = list[i] }
        }
    }

    private fun transform(rng: SeedRandom, swap: Boolean, square: Boolean): Transform {
        val rot = when {
            swap -> if (rng.nextDouble() < 0.5) 1 else 3
            square -> floor(rng.nextDouble() * 4).toInt()
            else -> if (rng.nextDouble() < 0.5) 0 else 2
        }
        return Transform(rot, rng.nextDouble() < 0.5, rng.nextDouble() < 0.5)
    }

    // Per-pixel copy of the site's canvas transform, including the tiles it clips
    private fun draw(pixels: IntArray, width: Int, src: Tile, out: IntArray, dst: Tile, tf: Transform) {
        val (r00, r01, r10, r11) = when (tf.rot) {
            1 -> listOf(0, 1, -1, 0)
            2 -> listOf(-1, 0, 0, -1)
            3 -> listOf(0, -1, 1, 0)
            else -> listOf(1, 0, 0, 1)
        }
        val odd = tf.rot % 2 == 1
        val outW = if (odd) src.h else src.w
        val outH = if (odd) src.w else src.h

        val sx = if (tf.flipH) -1 else 1
        val sy = if (tf.flipV) -1 else 1
        val a00 = r00 * sx
        val a01 = r01 * sy
        val a10 = r10 * sx
        val a11 = r11 * sy
        val t2x = if (tf.flipH) outW else 0
        val t2y = if (tf.flipV) outH else 0
        val (t1x, t1y) = when (tf.rot) {
            1 -> 0 to outW
            2 -> outW to outH
            3 -> outH to 0
            else -> 0 to 0
        }
        // Canvas maps pixel centres, so a negative axis is off by one
        val bx = r00 * t2x + r01 * t2y + t1x + if (a00 + a01 < 0) -1 else 0
        val by = r10 * t2x + r11 * t2y + t1y + if (a10 + a11 < 0) -1 else 0

        for (y in 0 until src.h) {
            val row = (src.y + y) * width + src.x
            for (x in 0 until src.w) {
                val ox = a00 * x + a01 * y + bx
                val oy = a10 * x + a11 * y + by
                if (ox in 0 until outW && oy in 0 until outH) {
                    out[(dst.y + oy) * width + dst.x + ox] = pixels[row + x]
                }
            }
        }
    }
}
