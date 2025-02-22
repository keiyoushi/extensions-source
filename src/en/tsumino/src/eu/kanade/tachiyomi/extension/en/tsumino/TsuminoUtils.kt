package eu.kanade.tachiyomi.extension.en.tsumino

import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Document

class TsuminoUtils {
    companion object {
        fun getArtists(document: Document): String {
            val stringBuilder = StringBuilder()
            val artists = document.select("#Artist a")

            artists.forEach {
                stringBuilder.append(it.attr("data-define"))

                if (it != artists.last()) {
                    stringBuilder.append(", ")
                }
            }

            return stringBuilder.toString()
        }

        private fun getGroups(document: Document): String? {
            val stringBuilder = StringBuilder()
            val groups = document.select("#Group a")

            groups.forEach {
                stringBuilder.append(it.attr("data-define"))

                if (it != groups.last()) {
                    stringBuilder.append(", ")
                }
            }

            return stringBuilder.toString().ifEmpty { null }
        }

        fun getDesc(document: Document): String {
            val stringBuilder = StringBuilder()
            val pages = document.select("#Pages").text()
            val parodies = document.select("#Parody a")
            val characters = document.select("#Character a")

            stringBuilder.append("Pages: $pages")

            if (parodies.size > 0) {
                stringBuilder.append("\n\n")
                stringBuilder.append("Parodies: ")

                parodies.forEach {
                    stringBuilder.append(it.attr("data-define"))

                    if (it != parodies.last()) {
                        stringBuilder.append(", ")
                    }
                }
            }

            if (characters.size > 0) {
                stringBuilder.append("\n\n")
                stringBuilder.append("Characters: ")

                characters.forEach {
                    stringBuilder.append(it.attr("data-define"))

                    if (it != characters.last()) {
                        stringBuilder.append(", ")
                    }
                }
            }

            return stringBuilder.toString()
        }

        fun getCollection(document: Document, selector: String): List<SChapter> {
            return document.select(selector).map { element ->
                SChapter.create().apply {
                    val chapterNum = element.select("span")[0].text()
                    val chapterName = element.select("span")[1].text()
                    name = "$chapterNum. $chapterName"
                    scanlator = getGroups(document)
                    url = element.attr("href").replace("entry", "Read/Index")
                }
            }.reversed()
        }

        fun getChapter(document: Document, response: Response): List<SChapter> {
            val chapterList = mutableListOf<SChapter>()
            val chapter = SChapter.create().apply {
                name = "Chapter"
                scanlator = getGroups(document)
                chapter_number = 1f
                url = response.request.url.encodedPath
                    .replace("entry", "Read/Index")
            }
            chapterList.add(chapter)
            return chapterList
        }

        fun cfDecodeEmails(document: Document) {
            document.select(".__cf_email__")
                .map { it to cfDecodeEmail(it.attr("data-cfemail")) }
                .forEach { (element, plaintext) -> element.text(plaintext) }
        }

        private fun cfDecodeEmail(encoded: String): String {
            val encodedList = encoded
                .chunked(2)
                .map { it.toIntOrNull(16) }

            val key = encodedList
                .firstOrNull()
                ?: return ""

            return encodedList
                .drop(1)
                .mapNotNull { it?.xor(key)?.toChar() }
                .joinToString("")
        }
    }
}
