package eu.kanade.tachiyomi.extension.pt.lermanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Activity that accepts https://lermanga.org/mangas/xxx intents and redirects them to
 * the main Tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main Tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi in spite of the DDoS blocking
 * the usual search screen from working.
 *
 * Added as the site removed their own search and are using an embedded Google search.
 * As Google has a lot of measures to prevent scrapping, this is the best that can
 * be done at the moment.
 */
class LerMangaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", LerManga.PREFIX_SLUG_SEARCH + slug)
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("LerMangaUrlActivity", e.toString())
            }
        } else {
            Log.e("LerMangaUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
