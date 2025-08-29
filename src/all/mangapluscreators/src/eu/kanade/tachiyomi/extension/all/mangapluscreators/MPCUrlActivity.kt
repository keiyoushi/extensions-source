package eu.kanade.tachiyomi.extension.all.mangapluscreators

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MPCUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            // {medibang.com/mpc,mangaplus-creators.jp}/{episodes,titles,authors}
            val pathIndex = if (intent?.data?.host?.startsWith("medibang") == true) 1 else 0
            val idIndex = pathIndex + 1
            val query = when {
                pathSegments[pathIndex].equals("episodes") -> {
                    MangaPlusCreators.PREFIX_EPISODE_ID_SEARCH + pathSegments[idIndex]
                }
                pathSegments[pathIndex].equals("authors") -> {
                    MangaPlusCreators.PREFIX_AUTHOR_ID_SEARCH + pathSegments[idIndex]
                }
                pathSegments[pathIndex].equals("titles") -> {
                    MangaPlusCreators.PREFIX_TITLE_ID_SEARCH + pathSegments[idIndex]
                }
                else -> null // TODO: is this required?
            }

            if (query != null) {
                val mainIntent = Intent().setAction("eu.kanade.tachiyomi.SEARCH").apply {
                    putExtra("query", query)
                    putExtra("filter", packageName)
                }

                try {
                    startActivity(mainIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("MPCUrlActivity", e.toString())
                }
            } else {
                Log.e("MPCUrlActivity", "Missing alphanumeric ID from the URL")
            }
        } else {
            Log.e("MPCUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
