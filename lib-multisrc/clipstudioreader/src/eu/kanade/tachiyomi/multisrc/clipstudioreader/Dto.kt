package eu.kanade.tachiyomi.multisrc.clipstudioreader

class FaceData(
    val totalPages: Int,
    val scrambleWidth: Int,
    val scrambleHeight: Int,
)

class PagePart(
    val fileName: String,
    val type: Int,
    val isScrambled: Boolean,
)
