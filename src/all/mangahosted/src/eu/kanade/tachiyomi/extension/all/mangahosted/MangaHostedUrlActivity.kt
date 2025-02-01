package eu.kanade.tachiyomi.extension.all.mangahosted

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MangaHostedUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", slug(pathSegments))
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("UnionMangasUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun slug(pathSegments: List<String>) =
        "${MangaHosted.SEARCH_PREFIX}${pathSegments[1]}"
}
