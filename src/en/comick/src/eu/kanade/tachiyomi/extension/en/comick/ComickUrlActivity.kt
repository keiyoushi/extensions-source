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
        if (pathSegments != null && pathSegments.size > 1 && pathSegments[0] == "comic") {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Comick.SLUG_SEARCH_PREFIX}$slug")
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
