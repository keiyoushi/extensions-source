package eu.kanade.tachiyomi.lib.speedbinb.descrambler

import eu.kanade.tachiyomi.lib.speedbinb.PtImgTranslation

private val PTBINBF_REGEX = Regex("""^=([0-9]+)-([0-9]+)([-+])([0-9]+)-([-_0-9A-Za-z]+)$""")
private const val PTBINBF_CHAR_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private const val PTBINBA_CHAR_LOOKUP = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ"

abstract class PtBinbDescrambler(
    val s: String,
    val u: String,
    val width: Int,
    val height: Int,
) : SpeedBinbDescrambler()

class PtBinbDescramblerF(s: String, u: String, width: Int, height: Int) : PtBinbDescrambler(s, u, width, height) {

    private var widthPieces: Int = 0
    private var heightPieces: Int = 0
    private var piecePadding: Int = 0
    private lateinit var hDstPosLookup: List<Int>
    private lateinit var wDstPosLookup: List<Int>
    private lateinit var hPosLookup: List<Int>
    private lateinit var wPosLookup: List<Int>
    private var pieceDest: List<Int>? = null

    init {
        // Kotlin init blocks don't allow early returns...
        init()
    }

    private fun init() {
        val srcData = PTBINBF_REGEX.matchEntire(s)?.groupValues
        val dstData = PTBINBF_REGEX.matchEntire(u)?.groupValues

        if (
            dstData == null ||
            srcData == null ||
            dstData[1] != srcData[1] ||
            dstData[2] != srcData[2] ||
            dstData[4] != srcData[4] ||
            dstData[3] != "+" ||
            srcData[3] != "-"
        ) {
            return
        }

        widthPieces = dstData[1].toInt()
        heightPieces = dstData[2].toInt()
        piecePadding = dstData[4].toInt()

        if (widthPieces < 8 || heightPieces < 8 || widthPieces * heightPieces < 64) {
            return
        }

        val e = widthPieces + heightPieces + widthPieces * heightPieces

        if (dstData[5].length != e || srcData[5].length != e) {
            return
        }

        val srcTnp = decodePieceData(srcData[5])
        val dstTnp = decodePieceData(dstData[5])

        hDstPosLookup = dstTnp.hPos
        wDstPosLookup = dstTnp.wPos
        hPosLookup = srcTnp.hPos
        wPosLookup = srcTnp.wPos
        pieceDest = buildList(widthPieces * heightPieces) {
            for (i in 0 until widthPieces * heightPieces) {
                add(dstTnp.pieces[srcTnp.pieces[i]])
            }
        }
    }

    override fun isScrambled() =
        pieceDest != null

    override fun canDescramble(): Boolean {
        val i = 2 * widthPieces * piecePadding
        val n = 2 * heightPieces * piecePadding

        return width >= 64 + i && height >= 64 + n && width * height >= (320 + i) * (320 + n)
    }

    override fun getCanvasDimensions(): Pair<Int, Int> {
        return if (canDescramble()) {
            Pair(
                width - 2 * widthPieces * piecePadding,
                height - 2 * heightPieces * piecePadding,
            )
        } else {
            Pair(width, height)
        }
    }

    override fun getDescrambleCoords(): List<PtImgTranslation> {
        val pieceDest = this.pieceDest

        if (!isScrambled() || pieceDest == null) {
            return emptyList()
        }

        if (!canDescramble()) {
            return listOf(
                PtImgTranslation(0, 0, width, height, 0, 0),
            )
        }

        val canvasWidth = width - 2 * widthPieces * piecePadding
        val canvasHeight = height - 2 * heightPieces * piecePadding
        val pieceWidth = (canvasWidth + widthPieces - 1).div(widthPieces)
        val remainderWidth = canvasWidth - (widthPieces - 1) * pieceWidth
        val pieceHeight = (canvasHeight + heightPieces - 1).div(heightPieces)
        val remainderHeight = canvasHeight - (heightPieces - 1) * pieceHeight

        return buildList(widthPieces * heightPieces) {
            for (o in 0 until widthPieces * heightPieces) {
                val hPos = o % widthPieces
                val wPos = o.div(widthPieces)
                val hDstPos = pieceDest[o] % widthPieces
                val wDstPos = pieceDest[o].div(widthPieces)

                add(
                    PtImgTranslation(
                        xsrc = piecePadding + hPos * (pieceWidth + 2 * piecePadding) + if (hPosLookup[wPos] < hPos) remainderWidth - pieceWidth else 0,
                        ysrc = piecePadding + wPos * (pieceHeight + 2 * piecePadding) + if (wPosLookup[hPos] < wPos) remainderHeight - pieceHeight else 0,
                        width = if (hPosLookup[wPos] == hPos) remainderWidth else pieceWidth,
                        height = if (wPosLookup[hPos] == wPos) remainderHeight else pieceHeight,
                        xdest = hDstPos * pieceWidth + if (hDstPosLookup[wDstPos] < hDstPos) remainderWidth - pieceWidth else 0,
                        ydest = wDstPos * pieceHeight + if (wDstPosLookup[hDstPos] < wDstPos) remainderHeight - pieceHeight else 0,
                    ),
                )
            }
        }
    }

    private fun decodePieceData(key: String): TNP {
        val wPos = buildList(widthPieces) {
            for (i in 0 until widthPieces) {
                add(PTBINBF_CHAR_LOOKUP.indexOf(key[i]))
            }
        }
        val hPos = buildList(heightPieces) {
            for (i in 0 until heightPieces) {
                add(PTBINBF_CHAR_LOOKUP.indexOf(key[widthPieces + i]))
            }
        }
        val pieces = buildList(widthPieces * heightPieces) {
            for (i in 0 until widthPieces * heightPieces) {
                add(PTBINBF_CHAR_LOOKUP.indexOf(key[widthPieces + heightPieces + i]))
            }
        }

        return TNP(wPos, hPos, pieces)
    }

    private class TNP(val wPos: List<Int>, val hPos: List<Int>, val pieces: List<Int>)
}

class PtBinbDescramblerA(s: String, u: String, width: Int, height: Int) : PtBinbDescrambler(s, u, width, height) {

    private var srcPieces: PieceCollection? = null

    private var dstPieces: PieceCollection? = null

    init {
        val srcPieces = calculatePieces(u)
        val dstPieces = calculatePieces(s)

        if (
            srcPieces != null &&
            dstPieces != null &&
            srcPieces.ndx == dstPieces.ndx &&
            srcPieces.ndy == dstPieces.ndy
        ) {
            this.srcPieces = srcPieces
            this.dstPieces = dstPieces
        }
    }

    override fun isScrambled() =
        srcPieces != null && dstPieces != null

    override fun canDescramble(): Boolean =
        width >= 64 && height >= 64 && width * height >= 102400

    override fun getCanvasDimensions(): Pair<Int, Int> =
        Pair(width, height)

    override fun getDescrambleCoords(): List<PtImgTranslation> {
        if (!isScrambled()) {
            return emptyList()
        }

        if (!canDescramble()) {
            return listOf(
                PtImgTranslation(0, 0, width, height, 0, 0),
            )
        }

        val srcPieces = this.srcPieces!!
        val dstPieces = this.dstPieces!!

        return buildList(srcPieces.piece.size + 2) {
            val n = width - width % 8
            val pieceWidth = (n - 1).div(7) - (n - 1).div(7) % 8
            val e = n - 7 * pieceWidth
            val s = height - height % 8
            val pieceHeight = (s - 1).div(7) - (s - 1).div(7) % 8
            val u = s - 7 * pieceHeight

            for (i in srcPieces.piece.indices) {
                val src = srcPieces.piece[i]
                val dst = dstPieces.piece[i]

                add(
                    PtImgTranslation(
                        xsrc = src.x.div(2) * pieceWidth + src.x % 2 * e,
                        ysrc = src.y.div(2) * pieceHeight + src.y % 2 * u,
                        width = src.w.div(2) * pieceWidth + src.w % 2 * e,
                        height = src.h.div(2) * pieceHeight + src.h % 2 * u,
                        xdest = dst.x.div(2) * pieceWidth + dst.x % 2 * e,
                        ydest = dst.y.div(2) * pieceHeight + dst.y % 2 * u,
                    ),
                )
            }

            val l = pieceWidth * (srcPieces.ndx - 1) + e
            val v = pieceHeight * (srcPieces.ndy - 1) + u

            if (l < width) {
                add(
                    PtImgTranslation(l, 0, width - l, v, l, 0),
                )
            }

            if (v < height) {
                add(
                    PtImgTranslation(0, v, width, height - v, 0, v),
                )
            }
        }
    }

    private fun calculatePieces(key: String): PieceCollection? {
        if (key.isEmpty()) {
            return null
        }

        val parts = key.split("-")

        if (parts.size != 3) {
            return null
        }

        val ndx = parts[0].toInt()
        val ndy = parts[1].toInt()
        val e = parts[2]

        if (ndx * ndy * 2 != e.length) {
            return null
        }

        val pieces = buildList(ndx * ndy) {
            val a = (ndx - 1) * (ndy - 1) - 1
            val f = ndx - 1 + a
            val c = ndy - 1 + f
            val l = 1 + c
            var w = 0
            var h = 0

            for (d in 0 until ndx * ndy) {
                val x = PTBINBA_CHAR_LOOKUP.indexOf(e[2 * d])
                val y = PTBINBA_CHAR_LOOKUP.indexOf(e[2 * d + 1])

                if (d <= a) {
                    h = 2
                    w = 2
                } else if (d <= f) {
                    h = 1
                    w = 2
                } else if (d <= c) {
                    h = 2
                    w = 1
                } else if (d <= l) {
                    h = 1
                    w = 1
                }

                add(Piece(x, y, w, h))
            }
        }

        return PieceCollection(ndx, ndy, pieces)
    }

    private class Piece(val x: Int, val y: Int, val w: Int, val h: Int)

    private class PieceCollection(val ndx: Int, val ndy: Int, val piece: List<Piece>)
}
