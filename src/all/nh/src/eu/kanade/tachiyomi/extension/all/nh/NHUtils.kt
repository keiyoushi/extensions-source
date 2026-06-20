package eu.kanade.tachiyomi.extension.all.nhentai

import org.jsoup.nodes.Element

object NHUtils {
    fun getArtists(data: Hentai): String {
        val artists = data.tags.filter { it.type == "artist" }
        return artists.joinToString(", ") { it.name }
    }

    fun getGroups(data: Hentai): String? {
        val groups = data.tags.filter { it.type == "group" }
        return groups.joinToString(", ") { it.name }.takeIf { it.isBlank() }
    }

    fun getTagDescription(data: Hentai): String {
        val tags = data.tags.groupBy { it.type }
        return buildString {
            tags["category"]?.joinToString { it.name }?.let {
                append("Categories: ", it, "\n")
            }
            tags["parody"]?.joinToString { it.name }?.let {
                append("Parodies: ", it, "\n")
            }
            tags["character"]?.joinToString { it.name }?.let {
                append("Characters: ", it, "\n\n")
            }
        }
    }

    fun getTags(data: Hentai): String {
        val artists = data.tags.filter { it.type == "tag" }
        return artists.joinToString(", ") { it.name }
    }

    private fun Element.cleanTag(): String = text().replace(Regex("\\(.*\\)"), "").trim()
}
