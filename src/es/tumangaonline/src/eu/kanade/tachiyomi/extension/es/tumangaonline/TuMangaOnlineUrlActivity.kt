package eu.kanade.tachiyomi.extension.es.tumangaonline

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://visortmo.com/library/:type/:id/:slug intents and redirects them to
 * the main Tachiyomi process.
 */
class TuMangaOnlineUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 3) {
            val type = pathSegments[1]
            val id = pathSegments[2]
            val slug = pathSegments[3]

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${TuMangaOnline.PREFIX_ID_SEARCH}$type/$id/$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("TMOUrlActivity", e.toString())
            }
        } else {
            Log.e("TMOUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
