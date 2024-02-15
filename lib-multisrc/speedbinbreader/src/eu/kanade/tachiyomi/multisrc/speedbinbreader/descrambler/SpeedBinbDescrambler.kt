package eu.kanade.tachiyomi.multisrc.speedbinbreader.descrambler

import eu.kanade.tachiyomi.multisrc.speedbinbreader.PtImgTranslation

interface SpeedBinbDescrambler {
    fun isScrambled(): Boolean
    fun canDescramble(): Boolean
    fun getCanvasDimensions(): Pair<Int, Int>
    fun getDescrambleCoords(): List<PtImgTranslation>
}
