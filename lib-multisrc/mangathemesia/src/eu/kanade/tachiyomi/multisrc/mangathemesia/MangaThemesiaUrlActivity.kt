package eu.kanade.tachiyomi.multisrc.mangathemesia

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MangaThemesiaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 1) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${MangaThemesia.URL_SEARCH_PREFIX}${intent?.data?.toString()}")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangaThemesiaUrlActivity", e.toString())
            }
        } else {
            Log.e("MangaThemesiaUrlActivity", "Could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
