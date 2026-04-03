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

        val data = intent?.data
        if (data != null && data.pathSegments != null && data.pathSegments.size > 1) {
            val path = data.pathSegments[0]
            val id = data.pathSegments[1]

            var query = "id:$id"
            when (path) {
                "chapter" -> {
                    query = "ch:$id"
                }
                "group" -> {
                    query = "grp:$id"
                }
                "user" -> {
                    query = "usr:$id"
                }
                "author" -> {
                    query = "author:$id"
                }
                "list" -> {
                    query = "list:$id"
                }
            }

            val mainIntent = Intent("eu.kanade.tachiyomi.SEARCH")
            mainIntent.putExtra("query", query)
            mainIntent.putExtra("filter", packageName)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangadexUrlActivity", "Activity not found: " + e.message)
            } catch (e: Throwable) {
                Log.e("MangadexUrlActivity", "Unexpected throwable: " + e.message)
            }
        } else {
            Log.e("MangadexUrlActivity", "Unable to parse URI: $data")
        }

        finish()
        exitProcess(0)
    }
}
