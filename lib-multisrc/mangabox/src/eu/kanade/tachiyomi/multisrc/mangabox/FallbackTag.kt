package eu.kanade.tachiyomi.multisrc.mangabox

class FallbackTag(
    private val isCdnProcessed: Boolean,
) {
    fun isProcessed(): Boolean {
        return isCdnProcessed
    }
}
