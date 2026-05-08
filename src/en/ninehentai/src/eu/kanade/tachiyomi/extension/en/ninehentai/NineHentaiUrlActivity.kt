package eu.kanade.tachiyomi.extension.en.ninehentai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://9hentai.so/g/xxxxxx intents and redirects them to
 * the main Tachiyomi process.
 */
class NineHentaiUrlActivity : Activity() {
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
            Log.e("NineHentaiUrlActivity", "Unable to launch activity", e)
        }

        finish()
        exitProcess(0)
    }
}
