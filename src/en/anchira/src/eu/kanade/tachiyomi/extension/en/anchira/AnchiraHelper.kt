package eu.kanade.tachiyomi.extension.en.anchira

import eu.kanade.tachiyomi.source.model.SChapter
import java.util.Locale

object AnchiraHelper {
    fun getPathFromUrl(url: String) = "${url.split("/").reversed()[1]}/${url.split("/").last()}"

    fun prepareTags(tags: List<Tag>, group: Boolean) = tags.map {
        if (it.namespace == null) {
            it.namespace = 6
        }
        it
    }
        .sortedBy { it.name }
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

    fun createChapter(entry: Entry, anchiraData: List<EntryKey>) =
        SChapter.create().apply {
            val chSuffix = CHAPTER_SUFFIX_RE.find(entry.title)?.value.orEmpty()
            val chNumber =
                chSuffix.replace(Regex("[^.\\d]"), "").trim('.').takeUnless { it.isEmpty() } ?: "1"
            val source = Regex("fakku|irodori").find(
                anchiraData.find { it.id == entry.id }?.url.orEmpty(),
            )?.value.orEmpty().titleCase()
            url = "/g/${entry.id}/${entry.key}"
            name = "$chNumber. ${entry.title.removeSuffix(chSuffix)}"
            date_upload = entry.publishedAt * 1000
            chapter_number = chNumber.toFloat()
            scanlator = buildString {
                if (source.isNotEmpty()) {
                    append("$source - ")
                }
                append("${entry.pages} pages")
            }
        }

    fun getCdn(page: Int) = if (page % 2 == 0) "https://kisakisexo.xyz" else "https://aronasexo.xyz"

    private fun String.titleCase() = replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }
}
