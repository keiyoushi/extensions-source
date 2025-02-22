package eu.kanade.tachiyomi.multisrc.readerfront

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts `{baseUrl}/work/{lang}/{stub}`
 * intents and redirects them to the main Tachiyomi process.
 */
class ReaderFrontActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val segments = intent?.data?.pathSegments
        if (segments != null && segments.size > 2) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", STUB_QUERY + segments[2])
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (ex: ActivityNotFoundException) {
                Log.e("ReaderFrontActivity", ex.message, ex)
            }
        } else {
            Log.e("ReaderFrontActivity", "Failed to parse URI from intent: $intent")
        }
        finish()
        exitProcess(0)
    }
}
