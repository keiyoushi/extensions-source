package eu.kanade.tachiyomi.extension.all.ehentai

import android.net.Uri

/**
 * Uri filter
 */
interface UriFilter {
    fun addToUri(builder: Uri.Builder)
}
