package eu.kanade.tachiyomi.extension.pt.tsukimangas

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://tsuki-mangas.com/obra/<id>/<item> intents
 * and redirects them to the main Tachiyomi process.
 */
class TsukiMangasUrlActivity : Activity() {
    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            val mainIntent =
                Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", "${TsukiMangas.PREFIX_SEARCH}$id")
                    putExtra("filter", packageName)
                }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, e.toString())
            }
        } else {
            Log.e(tag, "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
