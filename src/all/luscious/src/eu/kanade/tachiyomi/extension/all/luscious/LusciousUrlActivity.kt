package eu.kanade.tachiyomi.extension.all.luscious

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://www.luscious.net/albums/xxxxxx intents and redirects them to
 * the main Tachiyomi process.
 */
class LusciousUrlActivity : Activity() {
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
            Log.e("LusciousUrlActivity", "Unable to launch activity", e)
        }

        finish()
        exitProcess(0)
    }
}
