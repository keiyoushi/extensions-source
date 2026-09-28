package keiyoushi.lib.speedbinb.descrambler

import keiyoushi.lib.speedbinb.PtImg

class PtImgDescrambler(private val metadata: PtImg) : SpeedBinbDescrambler() {
    override fun isScrambled() = metadata.translations.isNotEmpty()

    override fun getCanvasDimensions(width: Int, height: Int) = Pair(metadata.views[0].width, metadata.views[0].height)

    override fun getDescrambleCoords(width: Int, height: Int) = metadata.translations
}
