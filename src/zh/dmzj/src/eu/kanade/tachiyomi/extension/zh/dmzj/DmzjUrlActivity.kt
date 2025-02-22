package eu.kanade.tachiyomi.extension.zh.dmzj

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://www.dmzj.com/info/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 */
class DmzjUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 0) {
            val titleId = if (pathSegments.size > 1) {
                pathSegments[1] // [m,www].dmzj.com/info/{titleId}
            } else {
                pathSegments[0] // manhua.dmzj.com/{titleId}
            }
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "$PREFIX_ID_SEARCH$titleId")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("DmzjUrlActivity", e.toString())
            }
        } else {
            Log.e("DmzjUrlActivity", "Could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
