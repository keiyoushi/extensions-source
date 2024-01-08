package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.extension.all.comickfun.ComickFun.Companion.dateFormat
import eu.kanade.tachiyomi.extension.all.comickfun.ComickFun.Companion.markdownItalicBoldRegex
import eu.kanade.tachiyomi.extension.all.comickfun.ComickFun.Companion.markdownItalicRegex
import eu.kanade.tachiyomi.extension.all.comickfun.ComickFun.Companion.markdownLinksRegex
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.parser.Parser

internal fun String.beautifyDescription(): String {
    return Parser.unescapeEntities(this, false)
        .substringBefore("---")
        .replace(markdownLinksRegex, "")
        .replace(markdownItalicBoldRegex, "")
        .replace(markdownItalicRegex, "")
        .trim()
}

internal fun Int?.parseStatus(translationComplete: Boolean?): Int {
    return when (this) {
        1 -> SManga.ONGOING
        2 -> {
            if (translationComplete == true) {
                SManga.COMPLETED
            } else {
                SManga.PUBLISHING_FINISHED
            }
        }
        3 -> SManga.CANCELLED
        4 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

internal fun parseCover(thumbnailUrl: String?, mdCovers: List<MDcovers>): String? {
    val b2key = mdCovers.firstOrNull()?.b2key
        ?: return thumbnailUrl
    val vol = mdCovers.firstOrNull()?.vol.orEmpty()

    return thumbnailUrl?.replaceAfterLast("/", "$b2key#$vol")
}

internal fun thumbnailIntercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val frag = request.url.fragment
    if (frag.isNullOrEmpty()) return chain.proceed(request)
    val response = chain.proceed(request)
    if (!response.isSuccessful && response.code == 404) {
        response.close()
        val url = request.url.toString()
            .replaceAfterLast("/", frag)

        return chain.proceed(
            request.newBuilder()
                .url(url)
                .build(),
        )
    }
    return response
}

internal fun beautifyChapterName(vol: String, chap: String, title: String): String {
    return buildString {
        if (vol.isNotEmpty()) {
            if (chap.isEmpty()) append("Volume $vol") else append("Vol. $vol")
        }
        if (chap.isNotEmpty()) {
            if (vol.isEmpty()) append("Chapter $chap") else append(", Ch. $chap")
        }
        if (title.isNotEmpty()) {
            if (chap.isEmpty()) append(title) else append(": $title")
        }
    }
}

internal fun String.parseDate(): Long {
    return runCatching { dateFormat.parse(this)?.time }
        .getOrNull() ?: 0L
}
