package eu.kanade.tachiyomi.extension.id.comicaso

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.extension.id.comicaso.Comicaso.Companion.URL_SEARCH_PREFIX
import kotlin.system.exitProcess

class UrlActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "$URL_SEARCH_PREFIX$data")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ComicasoUrl", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }
}
