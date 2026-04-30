package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class LxHentaiUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        if (data != null && data.pathSegments != null && data.pathSegments.size > 1) {
            val id = data.pathSegments[1]
            val mainIntent = Intent("eu.kanade.tachiyomi.SEARCH")
            mainIntent.putExtra("query", LxHentai.PREFIX_ID_SEARCH + id)
            mainIntent.putExtra("filter", packageName)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("LxHentaiUrlActivity", "Activity not found: " + e.message)
            } catch (e: Throwable) {
                Log.e("LxHentaiUrlActivity", "Error: " + e.message)
            }
        } else {
            Log.e("LxHentaiUrlActivity", "Unable to parse URI: $data")
        }

        finish()
        exitProcess(0)
    }
}
