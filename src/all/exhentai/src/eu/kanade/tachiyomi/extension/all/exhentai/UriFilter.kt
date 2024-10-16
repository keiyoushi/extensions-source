package eu.kanade.tachiyomi.extension.all.exhentai

import android.net.Uri

/**
 * Uri filter
 */
interface UriFilter {
    fun addToUri(builder: Uri.Builder)
}
