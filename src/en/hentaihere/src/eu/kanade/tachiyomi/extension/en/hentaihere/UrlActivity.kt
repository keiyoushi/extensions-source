package eu.kanade.tachiyomi.extension.en.hentaihere

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://hentaihere.com/m/Sxxxxx intents and redirects them to
 * the main Tachiyomi process.
 */
class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainIntent = Intent().apply {
            action = "eu.kanade.tachiyomi.SEARCH"
            putExtra("query", intent.data.toString())
            putExtra("filter", packageName)
        }

        try {
            startActivity(mainIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("UrlActivity", "Unable to launch activity", e)
        }

        finish()
        exitProcess(0)
    }
}
