package eu.kanade.tachiyomi.extension.en.kouhaiwork

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts `{baseUrl}/series/{id}`
 * intents and redirects them to the main Tachiyomi process.
 */
class KouhaiActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val segments = intent?.data?.pathSegments
        if (segments != null && segments.size > 1) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", ID_QUERY + segments[1])
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (ex: ActivityNotFoundException) {
                Log.e("KouhaiActivity", ex.message, ex)
            }
        } else {
            Log.e("KouhaiActivity", "Failed to parse URI from intent: $intent")
        }
        finish()
        exitProcess(0)
    }
}
