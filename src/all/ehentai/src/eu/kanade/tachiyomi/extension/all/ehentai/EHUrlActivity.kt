package eu.kanade.tachiyomi.extension.all.ehentai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://e-hentai.org/g/xxxxx/yyyyy/ intents and redirects them to
 * the main Tachiyomi process.
 */
class EHUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 2) {
            val id = pathSegments[1]
            val key = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${EHentai.PREFIX_ID_SEARCH}$id/$key")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("EHUrlActivity", e.toString())
            }
        } else {
            Log.e("EHUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
