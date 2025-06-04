package eu.kanade.tachiyomi.extension.tr.merlinscans

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MerlinScansUrlActivity : Activity() {
    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        val query = intent?.data?.query

        if (pathSegments != null && pathSegments.size > 0) {
            val item = pathSegments[0]
            val mainIntent = when {
                item == "series.php" && query?.contains("slug=") == true -> {
                    val slug = query.substringAfter("slug=").substringBefore("&")
                    val normalizedUrl = "/series/$slug"
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${MerlinScans::class.qualifiedName}:$normalizedUrl")
                        putExtra("filter", packageName)
                    }
                }

                item == "series" && pathSegments.size >= 2 -> {
                    val seriesSlug = pathSegments[1]
                    val normalizedUrl = "/series/$seriesSlug"
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${MerlinScans::class.qualifiedName}:$normalizedUrl")
                        putExtra("filter", packageName)
                    }
                }

                item == "series" && pathSegments.size >= 3 && pathSegments[2].startsWith("chapter-") -> {
                    val seriesSlug = pathSegments[1]
                    val normalizedUrl = "/series/$seriesSlug"
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${MerlinScans::class.qualifiedName}:$normalizedUrl")
                        putExtra("filter", packageName)
                    }
                }

                item == "all-series.php" -> {
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${MerlinScans::class.qualifiedName}:")
                        putExtra("filter", packageName)
                    }
                }

                else -> {
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${MerlinScans::class.qualifiedName}:")
                        putExtra("filter", packageName)
                    }
                }
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, e.toString())
            }
        } else {
            Log.e(tag, "URL'den path alınamadı")
        }
        finish()
        exitProcess(0)
    }
}
