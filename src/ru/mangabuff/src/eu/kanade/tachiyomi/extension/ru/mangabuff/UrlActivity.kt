package eu.kanade.tachiyomi.extension.ru.mangabuff

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${MangaBuff.SEARCH_PREFIX}$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("UrlActivity", e.toString())
            }
        } else {
            Log.e("UrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
