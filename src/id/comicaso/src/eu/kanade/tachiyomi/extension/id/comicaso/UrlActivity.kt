package eu.kanade.tachiyomi.extension.id.comicaso

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentData = intent?.data?.toString()
        if (intentData != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", intentData)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: Throwable) {
                Log.e("ComicasoUrl", e.toString())
            }
        } else {
            Log.e("ComicasoUrl", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
