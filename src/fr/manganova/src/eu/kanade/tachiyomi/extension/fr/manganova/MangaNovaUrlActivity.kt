package eu.kanade.tachiyomi.extension.fr.manganova

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://www.manganova.fr/lecture-en-ligne/xxxxxx &&
 * https://www.manganova.fr/manga/xxxxxx intents and redirects them to
 * the main Tachiyomi process.
 */
class MangaNovaUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 2) {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "SLUG:$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangaNovaUrlActivity", e.toString())
            }
        } else {
            Log.e("MangaNovaUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
