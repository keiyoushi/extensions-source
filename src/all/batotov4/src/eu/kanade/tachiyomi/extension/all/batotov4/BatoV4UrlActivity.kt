package eu.kanade.tachiyomi.extension.all.batotov4

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class BatoV4UrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = fromBatoV4(pathSegments)

            if (query == null) {
                Log.e("BatoV4UrlActivity", "Unable to parse URI from intent $intent")
                finish()
                exitProcess(1)
            }

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("BatoV4UrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromBatoV4(pathSegments: List<String>): String? {
        if (pathSegments.size < 2) return null
        val slug = pathSegments[1]
        val id = slug.substringBefore('-').takeIf { it.isNotBlank() } ?: return null
        return "ID:$id"
    }
}
