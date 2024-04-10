package eu.kanade.tachiyomi.multisrc.galleryadults

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat

object GalleryAdultsUtils {
    fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }!!

    fun getCover(document: Document): String {
        return document.selectFirst("#main-cover img")!!.imgAttr()
    }

    fun getArtists(document: Document): String {
        val artists = document.select("#main-info > div.tag-container:contains(Artists) > .filter-elem > a.name")
        return artists.joinToString(", ") { it.cleanTag() }
    }

    fun getGroups(document: Document): String? {
        val groups = document.select("#main-info > div.tag-container:contains(Groups) > .filter-elem > a.name")
        return if (groups.isNotEmpty()) {
            groups.joinToString(", ") { it.cleanTag() }
        } else {
            null
        }
    }

    fun getTagDescription(document: Document): String {
        val stringBuilder = StringBuilder()

        val categories = document.select("#main-info > div.tag-container:contains(Categories) > .filter-elem > a.name")
        if (categories.isNotEmpty()) {
            stringBuilder.append("Categories: ")
            stringBuilder.append(categories.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n\n")
        }

        val parodies = document.select("#main-info > div.tag-container:contains(Parodies) > .filter-elem > a.name")
        if (parodies.isNotEmpty()) {
            stringBuilder.append("Parodies: ")
            stringBuilder.append(parodies.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n")
        }

        val characters = document.select("#main-info > div.tag-container:contains(Characters) > .filter-elem > a.name")
        if (characters.isNotEmpty()) {
            stringBuilder.append("Characters: ")
            stringBuilder.append(characters.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n")
        }
        stringBuilder.append("\n")

        val languages = document.select("#main-info > div.tag-container:contains(Languages) > .filter-elem > a.name")
        if (languages.isNotEmpty()) {
            stringBuilder.append("Languages: ")
            stringBuilder.append(languages.joinToString(", ") { it.cleanTag() })
        }

        return stringBuilder.toString()
    }

    fun getTags(document: Document): String {
        val tags = document.select("#main-info > div.tag-container:contains(Tags) > .filter-elem > a.name")
        return tags.map { it.cleanTag() }.sorted().joinToString(", ")
    }

    fun getNumPages(document: Document): String {
        return document.selectFirst("#main-info > div.tag-container:contains(Pages) > span")!!.cleanTag()
    }

    fun getTime(document: Document, simpleDateFormat: SimpleDateFormat): Long {
        val timeString = document.selectFirst("#main-info > div.tag-container > time")
            ?.attr("datetime")
            ?.replace("T", " ")
            ?: ""

        return simpleDateFormat.tryParse(timeString)
    }

    private fun SimpleDateFormat.tryParse(string: String): Long {
        return try {
            parse(string)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    fun getCodes(document: Document): String? {
        val codes = document.select("#main-info > h3 > strong")
        if (codes.isNotEmpty()) {
            return "Code: "
                .plus(
                    codes.eachText().filterNotNull().joinToString {
                        it.replace("d", "3hentai - #")
                            .replace("g", "nhentai - #")
                    },
                )
                .plus("\n\n")
        }
        return null
    }

    private fun Element.cleanTag(): String = text().replace(Regex("\\(.*\\)"), "").trim()
}
