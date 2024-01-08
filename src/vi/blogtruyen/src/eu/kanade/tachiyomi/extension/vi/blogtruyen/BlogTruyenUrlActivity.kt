package eu.kanade.tachiyomi.extension.vi.blogtruyen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class BlogTruyenUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        with(pathSegments[0]) {
                            when {
                                equals("tac-gia") -> putExtra("query", "${BlogTruyen.PREFIX_AUTHOR_SEARCH}${pathSegments[1]}")
                                equals("nhom-dich") -> putExtra("query", "${BlogTruyen.PREFIX_TEAM_SEARCH}${pathSegments[1]}")
                                else -> putExtra("query", "${BlogTruyen.PREFIX_ID_SEARCH}${pathSegments[0]}/${pathSegments[1]}")
                            }
                        }
                        putExtra("filter", packageName)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                Log.e("BlogTruyenUrlActivity", e.toString())
            }
        } else {
            Log.e("BlogTruyenUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
