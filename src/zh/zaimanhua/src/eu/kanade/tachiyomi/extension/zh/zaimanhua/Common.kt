package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.ResponseBody
import java.util.zip.GZIPInputStream

fun ResponseBody.stringCompat(contentEncoding: String?): String {
    return if (contentEncoding == "gzip") {
        GZIPInputStream(byteStream()).bufferedReader().use { it.readText() }
    } else {
        string()
    }
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
