package eu.kanade.tachiyomi.extension.all.cubari

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class CubariUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = with(host) {
                when {
                    equals("m.imgur.com") || equals("imgur.com") -> fromSource("imgur", pathSegments)
                    equals("m.reddit.com") || equals("reddit.com") || equals("www.reddit.com") -> fromSource("reddit", pathSegments)
                    equals("imgchest.com") -> fromSource("imgchest", pathSegments)
                    equals("catbox.moe") || equals("www.catbox.moe") -> fromSource("catbox", pathSegments)
                    else -> fromCubari(pathSegments)
                }
            }

            if (query == null) {
                Log.e("CubariUrlActivity", "Unable to parse URI from intent $intent")
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
                Log.e("CubariUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromSource(source: String, pathSegments: List<String>): String? {
        if (pathSegments.size >= 2) {
            val id = pathSegments[1]

            return "${Cubari.PROXY_PREFIX}$source/$id"
        }
        return null
    }

    private fun fromCubari(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 3) {
            val source = pathSegments[1]
            val slug = pathSegments[2]
            "${Cubari.PROXY_PREFIX}$source/$slug"
        } else {
            null
        }
    }
}
