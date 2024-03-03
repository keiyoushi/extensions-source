package eu.kanade.tachiyomi.extension.en.anchira

import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.util.Locale

object AnchiraHelper {
    fun getPathFromUrl(url: String) = "${url.split("/").reversed()[1]}/${url.split("/").last()}"

    fun prepareTags(tags: List<Tag>, group: Boolean) = tags.map {
        if (it.namespace == null) {
            it.namespace = 6
        }
        it
    }
        .sortedBy { it.namespace }
        .map {
            val tag = it.name.lowercase()
            return@map if (group) {
                when (it.namespace) {
                    1 -> "artist:$tag"
                    2 -> "circle:$tag"
                    3 -> "parody:$tag"
                    4 -> "magazine:$tag"
                    else -> "tag:$tag"
                }
            } else {
                tag
            }
        }
        .joinToString(", ") { it }

    fun createChapter(entry: Entry, response: Response, anchiraData: List<EntryKey>) =
        SChapter.create().apply {
            val ch =
                Regex(CHAPTER_SUFFIX).find(entry.title)?.value?.trim('.') ?: "1"
            val source = anchiraData.find { it.id == entry.id }?.url
                ?: response.request.url.toString()
            url = "/g/${entry.id}/${entry.key}"
            name = "$ch. ${entry.title.removeSuffix(" $ch")}"
            date_upload = entry.publishedAt * 1000
            chapter_number = ch.toFloat()
            scanlator = buildString {
                append(
                    Regex("fakku|irodori|anchira").find(source)?.value.orEmpty()
                        .replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase(
                                    Locale.getDefault(),
                                )
                            } else {
                                it.toString()
                            }
                        },
                )
                append(" - ${entry.pages} pages")
            }
        }
}
