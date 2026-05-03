package eu.kanade.tachiyomi.extension.ru.unicomics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 2) {
            val titleid = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${UniComics.PREFIX_SLUG_SEARCH}$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: Throwable) {
                Log.e("UrlActivity", e.toString())
            }
        } else {
            Log.e("UrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
