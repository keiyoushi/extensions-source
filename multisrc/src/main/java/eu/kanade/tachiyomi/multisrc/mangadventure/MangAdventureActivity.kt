package eu.kanade.tachiyomi.multisrc.mangadventure

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts `{baseUrl}/reader/{slug}/`
 * intents and redirects them to the main Tachiyomi process.
 */
class MangAdventureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val segments = intent?.data?.pathSegments
        if (segments != null && segments.size > 1) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", MangAdventure.SLUG_QUERY + segments[1])
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (ex: ActivityNotFoundException) {
                Log.e("MangAdventureActivity", ex.message, ex)
            }
        } else {
            Log.e("MangAdventureActivity", "Failed to parse URI from intent: $intent")
        }
        finish()
        exitProcess(0)
    }
}
