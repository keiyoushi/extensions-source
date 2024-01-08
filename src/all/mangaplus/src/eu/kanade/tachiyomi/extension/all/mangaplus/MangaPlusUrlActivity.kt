package eu.kanade.tachiyomi.extension.all.mangaplus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MangaPlusUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            // Using Java's equals to not cause crashes in the activity.
            val query = when {
                pathSegments[0].equals("viewer") -> {
                    MangaPlus.PREFIX_CHAPTER_ID_SEARCH + pathSegments[1]
                }
                pathSegments[0].equals("sns_share") -> {
                    intent?.data?.getQueryParameter("title_id")
                        ?.let { MangaPlus.PREFIX_ID_SEARCH + it }
                }
                else -> MangaPlus.PREFIX_ID_SEARCH + pathSegments[1]
            }

            if (query != null) {
                val mainIntent = Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", query)
                    putExtra("filter", packageName)
                }

                try {
                    startActivity(mainIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("MangaPlusUrlActivity", e.toString())
                }
            } else {
                Log.e("MangaPlusUrlActivity", "Missing the title or chapter ID from the URL")
            }
        } else {
            Log.e("MangaPlusUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
