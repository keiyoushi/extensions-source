package eu.kanade.tachiyomi.extension.all.namicomi

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://namicomi.com/xx/title/yyy intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 */
class NamiComiUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments

        // Supported path: /en/title/12345
        if (pathSegments != null && pathSegments.size > 2) {
            val titleId = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", NamiComiConstants.prefixIdSearch + titleId)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("NamiComiUrlActivity", e.toString())
            }
        } else {
            Toast.makeText(this, "This URL cannot be handled by the Namicomi extension.", Toast.LENGTH_SHORT).show()
            Log.e("NamiComiUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
