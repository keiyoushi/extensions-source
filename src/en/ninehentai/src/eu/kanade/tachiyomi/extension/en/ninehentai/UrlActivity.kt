package eu.kanade.tachiyomi.extension.en.ninehentai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://9hentai.so/g/xxxxxx intents and redirects them to
 * the main Mihon process.
 */
class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = intent?.data?.toString()
        if (intentData != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", intentData)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: Throwable) {
                Log.e("UrlActivity", "Unable to launch activity", e)
            }
        } else {
            Log.e("UrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
