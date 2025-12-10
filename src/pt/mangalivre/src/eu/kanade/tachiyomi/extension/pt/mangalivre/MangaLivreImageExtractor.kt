package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.PriorityQueue

class MangaLivreImageExtractor(private val json: Json) {

    private val validDomains = listOf("mangalivre", "cdn", "r2d2storage", "aws", "amazon", "content")

    fun extractImageUrls(document: Document): List<Page> {
        return findImagesInDom(document)
            ?: throw Exception("Failed to load images. Please open in WebView.")
    }

    private fun findImagesInDom(document: Document): List<Page>? {
        val minHeap = PriorityQueue<Pair<Element, String>>(5) { a, b ->
            a.second.length.compareTo(b.second.length)
        }

        for (element in document.allElements) {
            if (element.attributesSize() == 0) continue

            for (attr in element.attributes()) {
                val value = attr.value

                if (minHeap.size < 5) {
                    minHeap.add(element to value)
                } else if (value.length > minHeap.peek().second.length) {
                    minHeap.poll()
                    minHeap.add(element to value)
                }
            }
        }

        while (minHeap.isNotEmpty()) {
            val (element, payload) = minHeap.poll()

            val keyCandidates = element.attributes().asList()
                .filter { it.value != payload }
                .map { it.value }
                .plus("")

            for (key in keyCandidates) {
                if (key.length > 100) continue

                val result = extractFromXorEncryption(payload, key)
                if (result != null) {
                    return result
                }
            }
        }

        return null
    }

    private fun extractFromXorEncryption(encryptedData: String, key: String): List<Page>? {
        return try {
            val bytes = Base64.decode(encryptedData, Base64.DEFAULT)

            val decryptedString = if (key.isNotEmpty()) {
                bytes.xorWith(key)
            } else {
                String(bytes)
            }

            parseJson(decryptedString)
        } catch (e: Exception) {
            null
        }
    }

    private fun ByteArray.xorWith(key: String): String {
        val keyBytes = key.toByteArray()
        val keyLen = keyBytes.size
        val output = CharArray(size)

        for (i in indices) {
            val b = this[i].toInt() and 0xFF
            val k = keyBytes[i % keyLen].toInt() and 0xFF
            output[i] = (b xor k).toChar()
        }
        return String(output)
    }

    private fun parseJson(text: String): List<Page>? {
        if (!text.trim().startsWith("[")) return null

        return runCatching {
            json.decodeFromString<List<String>>(text)
                .asSequence()
                .filter { isValidImageUrl(it) }
                .mapIndexed { i, url -> Page(i, imageUrl = url.trim()) }
                .toList()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun isValidImageUrl(url: String): Boolean {
        return url.contains("http", ignoreCase = true) &&
            validDomains.any { domain -> url.contains(domain, ignoreCase = true) }
    }
}
