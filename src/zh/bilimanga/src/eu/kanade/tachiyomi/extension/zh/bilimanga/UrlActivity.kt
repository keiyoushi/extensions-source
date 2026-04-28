package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        if (data != null) {
            val intent = Intent("eu.kanade.tachiyomi.SEARCH").apply {
                putExtra("query", data.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("BiliManga", "Unable to launch activity", e)
            }
        }

        finish()
        exitProcess(0)
    }
}
