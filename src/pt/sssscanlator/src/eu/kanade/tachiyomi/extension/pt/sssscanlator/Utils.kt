package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.extractNextJs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal fun extractSeriesPayload(document: Document, mangaSlug: String): SeriesPayloadDto {
    require(mangaSlug.isNotBlank()) { "Slug da obra nao encontrado na URL" }

    return document.extractNextJs<SeriesPayloadDto> { element ->
        element.matchesSeriesPayload(mangaSlug)
    } ?: throw IllegalStateException("Payload da obra nao encontrado para slug=$mangaSlug")
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

internal fun isStatusBadge(text: String): Boolean = when (text.lowercase()) {
    "em lancamento", "em lançamento", "ongoing" -> true
    "completo", "concluido", "concluído", "completed" -> true
    "hiato", "hiatus" -> true
    "cancelado", "canceled", "cancelled" -> true
    else -> false
}

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

    val chapters = payload["chapters"] as? JsonArray ?: return false
    val hasValidChapterShape = chapters.isEmpty() || chapters.any { chapter ->
        val chapterObject = chapter as? JsonObject ?: return@any false
        "id" in chapterObject && "number" in chapterObject
    }

    return "seriesId" in payload &&
        hasValidChapterShape &&
        (
            "totalChapters" in payload ||
                "coverImage" in payload ||
                "description" in payload
            )
}
