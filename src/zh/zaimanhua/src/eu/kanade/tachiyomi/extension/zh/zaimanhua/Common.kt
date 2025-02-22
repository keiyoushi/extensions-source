package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.ResponseBody
import uy.kohesive.injekt.injectLazy

val json: Json by injectLazy()

inline fun <reified T> Response.parseAs(): T {
    return json.decodeFromString(body.string())
}

inline fun <reified T> ResponseBody.parseAs(): T {
    return json.decodeFromString(this.string())
}

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

fun String.formatList() = replace("/", ", ")
