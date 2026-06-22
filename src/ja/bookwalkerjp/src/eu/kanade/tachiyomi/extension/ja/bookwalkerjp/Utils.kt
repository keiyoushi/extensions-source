package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val DOMAIN = "bookwalker.jp"
const val RIMG_URL = "https://rimg.$DOMAIN"
const val C_URL = "https://c.$DOMAIN"

internal fun String?.getHiResCoverFromLegacyUrl(): String? {
    if (this.isNullOrEmpty()) return null
    val segments = this.toHttpUrlOrNull()?.pathSegments ?: return null
    val fileName = segments.last()
    val extension = fileName.substringAfterLast('.')
    val coverId = when {
        this.startsWith(RIMG_URL) -> {
            val id = segments.first().reversed().toLongOrNull() ?: return null
            (id - 1).toString()
        }
        fileName.startsWith("thumbnailImage_") -> {
            val raw = fileName.substringAfter("thumbnailImage_").substringBefore('.')
            raw.toLongOrNull()?.let { (it - 1).toString() } ?: raw
        }
        else -> return null
    }

    return "$C_URL/coverImage_$coverId.$extension#$this"
}
