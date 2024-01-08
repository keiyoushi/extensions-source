package eu.kanade.tachiyomi.extension.all.mangaup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MangaUpUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val query = pathSegments[1]

            if (query != null) {
                val mainIntent = Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", MangaUp.PREFIX_ID_SEARCH + query)
                    putExtra("filter", packageName)
                }

                try {
                    startActivity(mainIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("MangaPlusUrlActivity", e.toString())
                }
            } else {
                Log.e("MangaUpUrlActivity", "Missing the title ID from the URL")
            }
        } else {
            Log.e("MangaUpUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
