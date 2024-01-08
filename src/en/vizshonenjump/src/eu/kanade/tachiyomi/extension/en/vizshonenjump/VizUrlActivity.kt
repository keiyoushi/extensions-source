package eu.kanade.tachiyomi.extension.en.vizshonenjump

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class VizUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments: List<String>? = intent?.data?.pathSegments
        if (!pathSegments.isNullOrEmpty() && pathSegments.size >= 3) {
            // Have to use .equals, otherwise get an error 'Didn't find class "kotlin.jvm.internal.Intrinsics"'
            val seriesSlug = if (pathSegments[2].equals("chapter") && contains(pathSegments[1], "-chapter-")) {
                substringBeforeLast(pathSegments[1], "-chapter-")
            } else {
                pathSegments[2]
            }
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra(
                    "query",
                    "${Viz.PREFIX_URL_SEARCH}/${pathSegments[0]}/chapters/$seriesSlug",
                )
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("VizUrlActivity", "failed to start activity with error: $e")
            }
        } else {
            Log.e("VizUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun containsAt(haystack: String, startIndex: Int, needle: String): Boolean {
        for (i in 0 until needle.length) {
            if (needle[i] != haystack[startIndex + i]) {
                return false
            }
        }
        return true
    }

    private fun contains(haystack: String, needle: String): Boolean {
        if (needle.length > haystack.length) {
            return false
        }
        for (startIndex in 0..haystack.length - needle.length) {
            if (containsAt(haystack, startIndex, needle)) {
                return true
            }
        }
        return false
    }

    private fun substringBeforeLast(haystack: String, needle: String): String {
        if (needle.length > haystack.length) {
            return haystack
        }
        for (startIndex in (haystack.length - needle.length) downTo 0) {
            if (containsAt(haystack, startIndex, needle)) {
                return haystack.subSequence(0, startIndex).toString()
            }
        }
        return haystack
    }
}
