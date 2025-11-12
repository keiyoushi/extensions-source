package eu.kanade.tachiyomi.extension.pt.sussyscan

import okhttp3.HttpUrl
import java.text.Normalizer

fun HttpUrl.Builder.addQueryParameterIfNotEmpty(name: String, value: String?): HttpUrl.Builder {
    if (!value.isNullOrEmpty()) {
        addQueryParameter(name, value)
    }
    return this
}

fun HttpUrl.Builder.addQueryParameterIf(predicate: Boolean, name: String, value: String): HttpUrl.Builder {
    if (predicate) {
        addQueryParameter(name, value)
    }
    return this
}

fun String.toSlug(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .trim()
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("\\p{Punct}".toRegex(), "")
        .replace("\\s+".toRegex(), "-")
        .lowercase()
}

fun String.toPathSegment(): String {
    return trim().split("/")
        .filter(String::isNotEmpty)
        .joinToString("/")
}

fun Float.isNotInteger(): Boolean = toInt() < this
