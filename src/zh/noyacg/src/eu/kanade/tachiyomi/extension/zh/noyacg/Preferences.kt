package eu.kanade.tachiyomi.extension.zh.noyacg

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import kotlin.random.Random

fun getPreferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        val count = IMAGE_CDN.size
        key = IMAGE_CDN_PREF
        title = "图片分流（重启生效）"
        summary = "%s"
        entries = Array(count) { "分流 ${it + 1}" }
        entryValues = Array(count) { "$it" }
    },
)

val SharedPreferences.imageCdn: String
    get() {
        val imageCdn = IMAGE_CDN
        var index = getString(IMAGE_CDN_PREF, "-1")!!.toInt()
        if (index !in imageCdn.indices) {
            index = Random.nextInt(0, imageCdn.size)
            edit().putString(IMAGE_CDN_PREF, index.toString()).apply()
        }
        return "https://" + imageCdn[index]
    }

const val IMAGE_CDN_PREF = "IMAGE_CDN"
val IMAGE_CDN get() = arrayOf("img.noy.asia", "img.noyteam.online", "img.457475.xyz")
