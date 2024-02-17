package eu.kanade.tachiyomi.lib.speedbinb.descrambler

import eu.kanade.tachiyomi.lib.speedbinb.PtImg

class PtImgDescrambler(private val metadata: PtImg) : SpeedBinbDescrambler() {
    override fun isScrambled() = metadata.translations.isNotEmpty()

    override fun canDescramble() = metadata.translations.isNotEmpty()

    override fun getCanvasDimensions() = Pair(metadata.views[0].width, metadata.views[0].height)

    override fun getDescrambleCoords() = metadata.translations
}
