package eu.kanade.tachiyomi.multisrc.speedbinb.descrambler

import eu.kanade.tachiyomi.multisrc.speedbinb.PtImgTranslation

interface SpeedBinbDescrambler {
    fun isScrambled(): Boolean
    fun canDescramble(): Boolean
    fun getCanvasDimensions(): Pair<Int, Int>
    fun getDescrambleCoords(): List<PtImgTranslation>
}
