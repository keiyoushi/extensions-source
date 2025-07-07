package eu.kanade.tachiyomi.extension.all.pixiv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://mangadex.com/title/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi in spite of the DDoS blocking
 * the usual search screen from working.
 */
class PixivUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.data != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", intent.data.toString())
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PixivUrlActivity", e.toString())
            }
        } else {
            Log.e("PixivUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
