package eu.kanade.tachiyomi.extension.ja.mangahotweb

import android.util.Base64
import keiyoushi.utils.obj
import keiyoushi.utils.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import org.jsoup.select.Elements

@Serializable
class StoriesDto(
    private val result: String,
) {
    fun stories(baseUrl: String): Elements = Jsoup.parseBodyFragment(result, baseUrl).select("a.parent[id^=STORY]")
}

@Serializable
class PageFilesDto(
    private val result: JsonElement,
) {
    // Base64 paths, in a list for the first pages and in an object keyed by page index after them
    fun files(): Map<Int, String> {
        val files = if (result is JsonArray) {
            result.withIndex().associate { (index, file) -> index to file.string }
        } else {
            result.obj.entries.associate { (index, file) -> index.toInt() to file.string }
        }
        return files.mapValues { (_, file) -> Base64.decode(file, Base64.DEFAULT).decodeToString() }
    }
}
