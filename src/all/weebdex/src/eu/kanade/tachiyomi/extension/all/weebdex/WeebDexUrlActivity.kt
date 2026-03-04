package eu.kanade.tachiyomi.extension.all.weebdex

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class WeebDexUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val titleId = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                with(pathSegments[0]) {
                    when {
                        equals("chapter") -> putExtra("query", WeebDexConstants.PREFIX_CH_SEARCH + titleId)
                        else -> putExtra("query", WeebDexConstants.PREFIX_ID_SEARCH + titleId)
                    }
                }
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("WeebDexUrlActivity", e.toString())
            }
        } else {
            Log.e("WeebDexUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
