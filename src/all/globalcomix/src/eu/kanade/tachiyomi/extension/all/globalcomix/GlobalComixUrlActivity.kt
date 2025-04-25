package eu.kanade.tachiyomi.extension.all.globalcomix

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://globalcomix.com/c/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 */
class GlobalComixUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments

        // Supported path: /c/title-slug
        if (pathSegments != null && pathSegments.size > 2) {
            val titleId = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", GlobalComixConstants.prefixIdSearch + titleId)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("GlobalComixUrlActivity", e.toString())
            }
        } else {
            Log.e("GlobalComixUrlActivity", "Received data URL is unsupported: ${intent?.data}")
        }

        finish()
        exitProcess(0)
    }
}
