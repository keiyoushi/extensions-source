package eu.kanade.tachiyomi.extension.all.webtoons

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
class WebtoonsUrlActivity : Activity() {

    private val name = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val titleNo = intent?.data?.getQueryParameter("title_no")
        val path = intent?.data?.pathSegments
        if (titleNo != null && path != null && path.size >= 3) {
            val lang = path[0]
            val type = path[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "$ID_SEARCH_PREFIX$type:$lang:$titleNo")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(name, e.toString())
            }
        } else {
            Log.e(name, "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
