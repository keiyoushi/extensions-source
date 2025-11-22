package eu.kanade.tachiyomi.extension.all.yskcomics

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts `{baseUrl}/{lang}/comic/{stub}`
 * intents and redirects them to the main Tachiyomi process.
 */
class YSKComicsUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data != null) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", data.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (ex: ActivityNotFoundException) {
                Log.e("YSKComicsUrlActivity", ex.message, ex)
            }
        } else {
            Log.e("YSKComicsUrlActivity", "Failed to parse URI from intent: $intent")
        }
        finish()
        exitProcess(0)
    }
}
