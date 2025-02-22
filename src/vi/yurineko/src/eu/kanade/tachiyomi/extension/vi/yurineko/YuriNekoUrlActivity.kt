package eu.kanade.tachiyomi.extension.vi.yurineko

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class YuriNekoUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        with(pathSegments[0]) {
                            when {
                                equals("manga") -> putExtra("query", "${YuriNeko.PREFIX_ID_SEARCH}$id")
                                equals("origin") -> putExtra("query", "${YuriNeko.PREFIX_DOUJIN_SEARCH}$id")
                                equals("author") -> putExtra("query", "${YuriNeko.PREFIX_AUTHOR_SEARCH}$id")
                                equals("tag") -> putExtra("query", "${YuriNeko.PREFIX_TAG_SEARCH}$id")
                                equals("couple") -> putExtra("query", "${YuriNeko.PREFIX_COUPLE_SEARCH}$id")
                                equals("team") -> putExtra("query", "${YuriNeko.PREFIX_TEAM_SEARCH}$id")
                                else -> putExtra("query", "${YuriNeko.PREFIX_ID_SEARCH}$id")
                            }
                        }
                        putExtra("filter", packageName)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                Log.e("YuriNekoUrlActivity", e.toString())
            }
        } else {
            Log.e("YuriNekoUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
