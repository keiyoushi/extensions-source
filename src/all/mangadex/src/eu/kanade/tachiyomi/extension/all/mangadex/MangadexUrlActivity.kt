package eu.kanade.tachiyomi.extension.all.mangadex

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
class MangadexUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val titleId = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                with(pathSegments[0]) {
                    when {
                        equals("chapter") -> putExtra("query", MDConstants.prefixChSearch + titleId)
                        equals("group") -> putExtra("query", MDConstants.prefixGrpSearch + titleId)
                        equals("user") -> putExtra("query", MDConstants.prefixUsrSearch + titleId)
                        equals("author") -> putExtra("query", MDConstants.prefixAuthSearch + titleId)
                        equals("list") -> putExtra("query", MDConstants.prefixListSearch + titleId)
                        else -> putExtra("query", MDConstants.prefixIdSearch + titleId)
                    }
                }
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangadexUrlActivity", e.toString())
            }
        } else {
            Log.e("MangadexUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
