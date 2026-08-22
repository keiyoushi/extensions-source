package eu.kanade.tachiyomi.extension.en.comick

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Opens shared comick.dev comic links inside the reader app.
 */
class ComickUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        val hidOrSlug = when {
            pathSegments == null || pathSegments.isEmpty() || pathSegments[0] != "comic" -> null
            pathSegments.size == 2 -> pathSegments[1]
            pathSegments.size >= 3 -> {
                // /comic/<slug>/<hid>-chapter-<n>  → hid is at index 2
                val chapterSegment = pathSegments[2]
                chapterSegment.substringBefore("-")
                    .takeIf { it.isNotBlank() } ?: pathSegments[1]
            }
            else -> null
        }
        if (hidOrSlug != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Comick.SLUG_SEARCH_PREFIX}$hidOrSlug")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ComickUrlActivity", e.toString())
            }
        } else {
            Log.e("ComickUrlActivity", "could not parse uri from intent $intent")
        }
        finish()
    }
}
