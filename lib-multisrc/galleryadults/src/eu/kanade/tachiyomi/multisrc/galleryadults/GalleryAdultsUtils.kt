package eu.kanade.tachiyomi.multisrc.galleryadults

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar

object GalleryAdultsUtils {
    fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }!!

    fun String?.toDate(simpleDateFormat: SimpleDateFormat?): Long {
        this ?: return 0L

        return if (simpleDateFormat != null) {
            if (contains(Regex("""\d(st|nd|rd|th)"""))) {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { simpleDateFormat.tryParse(it.joinToString(" ")) }
            } else {
                simpleDateFormat.tryParse(this)
            }
        } else {
            parseDate(this)
        }
    }

    fun parseDate(date: String?): Long {
        date ?: return 0

        return when {
            // Handle 'yesterday' and 'today', using midnight
            WordSet("yesterday", "يوم واحد").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1) // yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("today", "just now").startsWith(date) -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("يومين").startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            WordSet("ago", "atrás", "önce", "قبل").endsWith(date) -> {
                parseRelativeDate(date)
            }
            WordSet("hace").startsWith(date) -> {
                parseRelativeDate(date)
            }
            else -> 0L
        }
    }

    // Parses dates in this form: 21 hours ago OR "2 days ago (Updated 19 hours ago)"
    fun parseRelativeDate(date: String): Long {
        val number = Regex("""\d*[^0-9]*(\d+)""").find(date)?.value?.toIntOrNull()
            ?: date.split(" ").firstOrNull()
                ?.replace("one", "1")
                ?.replace("a", "1")
                ?.toIntOrNull()
            ?: return 0L
        val now = Calendar.getInstance()

        // Sort by order
        return when {
            WordSet("detik", "segundo", "second", "วินาที").anyWordIn(date) ->
                now.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").anyWordIn(date) ->
                now.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة", "小时").anyWordIn(date) ->
                now.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام", "天").anyWordIn(date) ->
                now.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("week", "semana").anyWordIn(date) ->
                now.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("month", "mes").anyWordIn(date) ->
                now.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("year", "año").anyWordIn(date) ->
                now.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0L
        }
    }

    fun SimpleDateFormat.tryParse(string: String): Long {
        return try {
            parse(string)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    class WordSet(private vararg val words: String) {
        fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
        fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
        fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
    }

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

    fun Element.cleanTag(): String = text().replace(Regex("\\(.*\\)"), "").trim()
    fun String.cleanTag(): String = replace(Regex("\\(.*\\)"), "").trim()
}
