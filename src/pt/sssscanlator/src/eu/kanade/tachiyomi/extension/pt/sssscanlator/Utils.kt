package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal fun extractSeriesPayload(document: Document, mangaSlug: String): SeriesPayloadDto {
    require(mangaSlug.isNotBlank()) { "Slug da obra nao encontrado na URL" }

    val matches = mutableListOf<JsonElement>()
    document.extractNextJs<JsonElement> { element ->
        if (element.matchesSeriesPayload(mangaSlug)) matches.add(element)
        false
    }

    val chapterArrays = matches.takeIf { it.isNotEmpty() }?.map { it.parseAs<JsonObject>()["capitulos_lista"]!!.parseAs<JsonArray>() }
        ?: error("Payload da obra nao encontrado para slug=$mangaSlug")

    return matches[selectRealArray(chapterArrays)!!].parseAs()
}

internal fun extractBadgeTexts(titleElement: Element?): List<String> {
    val nearbyElements = listOfNotNull(
        titleElement,
        titleElement?.previousElementSibling(),
        titleElement?.nextElementSibling(),
        titleElement?.nextElementSibling()?.nextElementSibling(),
        titleElement?.parent(),
        titleElement?.parent()?.parent(),
    )

    return nearbyElements
        .flatMap { element -> element.select("span[data-slot=badge]") }
        .map { it.text() }
        .filter(String::isNotEmpty)
        .distinct()
}

internal fun isStatusBadge(text: String): Boolean = parseStatus(text) != SManga.UNKNOWN

internal fun parseStatus(statusText: String?): Int = when (statusText?.lowercase()) {
    "em lancamento", "em lançamento", "ongoing" -> SManga.ONGOING
    "completo", "concluido", "concluído", "completed" -> SManga.COMPLETED
    "hiato", "hiatus" -> SManga.ON_HIATUS
    "cancelado", "canceled", "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun JsonElement.matchesSeriesPayload(expectedSlug: String): Boolean {
    val payload = this as? JsonObject ?: return false
    if (payload["slug"]?.jsonPrimitive?.contentOrNull != expectedSlug) return false

    val chapters = payload["capitulos_lista"] as? JsonArray ?: return false
    val hasValidChapterShape = chapters.isEmpty() || chapters.any { chapter ->
        val chapterObject = chapter as? JsonObject ?: return@any false
        "id" in chapterObject && "number" in chapterObject
    }

    return hasValidChapterShape &&
        (
            "chapterTotal" in payload ||
                "refId" in payload ||
                "coverImage" in payload ||
                "description" in payload
            )
}

// ignore fake matches with dummy fields
fun selectRealArray(arrays: List<JsonArray>): Int? {
    if (arrays.isEmpty()) return null
    if (arrays.size == 1) return 0

    val emptyIndex = arrays.indexOfFirst { it.isEmpty() }
    if (emptyIndex != -1) return emptyIndex

    return arrays.indices.maxByOrNull { index ->
        val array = arrays[index]
        array.sumOf { it.toString().length }.toDouble() / array.size
    }
}
