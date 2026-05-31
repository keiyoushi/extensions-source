package eu.kanade.tachiyomi.extension.all.beauty3600000

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

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
            } catch (e: Exception) {
                Log.e("Beauty3600000", "Failed to start activity: $e")
            }
        }
        finish()
    }
}
