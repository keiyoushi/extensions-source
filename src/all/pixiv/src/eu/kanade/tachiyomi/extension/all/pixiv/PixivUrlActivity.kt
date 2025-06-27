package eu.kanade.tachiyomi.extension.all.pixiv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.extension.all.pixiv.PixivConstants.ID_ILLUST_PREFIX
import eu.kanade.tachiyomi.extension.all.pixiv.PixivConstants.ID_SERIES_PREFIX
import eu.kanade.tachiyomi.extension.all.pixiv.PixivConstants.ID_USER_PREFIX
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
class PixivUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var couldBuildIntent = false

        do {
            var pathSegments = intent?.data?.pathSegments ?: break

            if ("en".equals(pathSegments[0])) {
                pathSegments = pathSegments.subList(1, pathSegments.size)
            }
            if (pathSegments.size < 2) break

            val query = with(pathSegments[0]) {
                when {
                    equals("artworks") -> "${ID_ILLUST_PREFIX}${pathSegments[1]}"
                    equals("users") -> "${ID_USER_PREFIX}${pathSegments[1]}"
                    equals("user") &&
                        (pathSegments.size >= 4 && pathSegments[2].equals("series")) ->
                        "${ID_SERIES_PREFIX}${pathSegments[3]}"
                    else -> null
                }
            }
            if (query == null) break

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }
            couldBuildIntent = true

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PixivUrlActivity", e.toString())
            }
        } while (false)

        if (!couldBuildIntent) {
            Log.e("PixivUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
