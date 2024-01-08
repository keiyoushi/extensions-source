package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.net.URLDecoder

const val PREFIX_ID_SEARCH = "id:"

val json: Json by injectLazy()

inline fun <reified T> Response.parseAs(): T {
    return json.decodeFromString(body.string())
}

fun getMangaUrl(id: String) = "/comic/comic_$id.json?version=2.7.019"

fun String.extractMangaId(): String {
    val start = 13 // length of "/comic/comic_"
    return substring(start, indexOf('.', start))
}

fun String.formatList() = replace("/", ", ")

fun parseStatus(status: String): Int = when (status) {
    "连载中" -> SManga.ONGOING
    "已完结" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private val chapterNameRegex = Regex("""(?:连载版?)?(\d[.\d]*)([话卷])?""")

fun String.formatChapterName(): String {
    val match = chapterNameRegex.matchEntire(this) ?: return this
    val (number, optionalType) = match.destructured
    val type = optionalType.ifEmpty { "话" }
    return "第$number$type"
}

fun parsePageList(
    images: List<String>,
    lowResImages: List<String> = List(images.size) { "" },
): ArrayList<Page> {
    val pageCount = images.size
    val list = ArrayList<Page>(pageCount + 1) // for comments page
    for (i in 0 until pageCount) {
        list.add(Page(i, lowResImages[i], images[i]))
    }
    return list
}

fun String.decodePath(): String = URLDecoder.decode(this, "UTF-8")

const val COMMENTS_FLAG = "COMMENTS"
