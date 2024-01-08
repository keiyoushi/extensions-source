package eu.kanade.tachiyomi.extension.all.nhentai

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

object NHUtils {
    fun getArtists(document: Document): String {
        val artists = document.select("#tags > div:nth-child(4) > span > a .name")
        return artists.joinToString(", ") { it.cleanTag() }
    }

    fun getGroups(document: Document): String? {
        val groups = document.select("#tags > div:nth-child(5) > span > a .name")
        return if (groups.isNotEmpty()) {
            groups.joinToString(", ") { it.cleanTag() }
        } else {
            null
        }
    }

    fun getTagDescription(document: Document): String {
        val stringBuilder = StringBuilder()

        val categories = document.select("#tags > div:nth-child(7) > span > a .name")
        if (categories.isNotEmpty()) {
            stringBuilder.append("Categories: ")
            stringBuilder.append(categories.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n\n")
        }

        val parodies = document.select("#tags > div:nth-child(1) > span > a .name")
        if (parodies.isNotEmpty()) {
            stringBuilder.append("Parodies: ")
            stringBuilder.append(parodies.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n\n")
        }

        val characters = document.select("#tags > div:nth-child(2) > span > a .name")
        if (characters.isNotEmpty()) {
            stringBuilder.append("Characters: ")
            stringBuilder.append(characters.joinToString(", ") { it.cleanTag() })
        }

        return stringBuilder.toString()
    }

    fun getTags(document: Document): String {
        val tags = document.select("#tags > div:nth-child(3) > span > a .name")
        return tags.map { it.cleanTag() }.sorted().joinToString(", ")
    }

    fun getNumPages(document: Document): String {
        return document.select("#tags > div:nth-child(8) > span > a .name").first()!!.cleanTag()
    }

    fun getTime(document: Document): Long {
        val timeString = document.toString().substringAfter("datetime=\"").substringBefore("\">").replace("T", " ")

        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZ").parse(timeString)?.time ?: 0L
    }

    private fun Element.cleanTag(): String = text().replace(Regex("\\(.*\\)"), "").trim()
}
