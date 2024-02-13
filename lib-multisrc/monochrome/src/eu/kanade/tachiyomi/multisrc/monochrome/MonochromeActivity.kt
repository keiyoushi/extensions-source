package eu.kanade.tachiyomi.multisrc.monochrome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts `{baseUrl}/manga/{uuid}`
 * intents and redirects them to the main Tachiyomi process.
 */
class MonochromeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val segments = intent?.data?.pathSegments
        if (segments != null && segments.size > 1) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", UUID_QUERY + segments[1])
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (ex: ActivityNotFoundException) {
                Log.e("MonochromeActivity", ex.message, ex)
            }
        } else {
            Log.e("MonochromeActivity", "Failed to parse URI from intent: $intent")
        }
        finish()
        exitProcess(0)
    }
}
