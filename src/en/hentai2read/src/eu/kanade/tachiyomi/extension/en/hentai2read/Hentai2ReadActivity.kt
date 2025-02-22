package eu.kanade.tachiyomi.extension.en.hentai2read

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://hentai2read.com/xxxx intents
 * and redirects them to the main Tachiyomi process.
 */
class Hentai2ReadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null) {
            // TODO: filter standard paths
            val id = pathSegments[0]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Hentai2Read.PREFIX_ID_SEARCH}$id")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("Hentai2ReadActivity", e.toString())
            }
        } else {
            Log.e("Hentai2ReadActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
