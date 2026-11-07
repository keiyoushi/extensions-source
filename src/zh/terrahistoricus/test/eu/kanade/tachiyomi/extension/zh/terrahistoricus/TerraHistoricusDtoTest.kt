package eu.kanade.tachiyomi.extension.zh.terrahistoricus

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TerraHistoricusDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesOfficialCatalogWithNullSubtitle() {
        val input = checkNotNull(javaClass.getResourceAsStream("/terra-historicus-comics.json"))
            .bufferedReader().use { it.readText() }
        val comics = json.decodeFromString<THResult<List<THComic>>>(input).data

        assertEquals(22, comics.size)
        assertEquals("3114", comics.first().cid)
        assertEquals("序言组曲：火与路", comics.first().title)
        val description = THComic::class.java.getDeclaredMethod("getDescription").apply {
            isAccessible = true
        }
        assertNull(description.invoke(comics.first()))
    }

    @Test
    fun decodesOfficialDetailsWithNullSubtitle() {
        val input = fixture("comic-3114.json")
        val comic = json.decodeFromString<THResult<THComic>>(input).data
        val introduction = json.parseToJsonElement(input).jsonObject.getValue("data")
            .jsonObject["introduction"]?.jsonPrimitive?.contentOrNull

        assertEquals("3114", comic.cid)
        assertEquals(2, comic.episodes?.size)
        assertEquals(introduction, description(comic))
    }

    @Test
    fun preservesNonemptySubtitleAndIntroduction() {
        val comic = json.decodeFromJsonElement<THComic>(
            catalogEntry(
                "subtitle" to JsonPrimitive("副标题"),
                "introduction" to JsonPrimitive("简介"),
            ),
        )

        assertEquals("「副标题」\n简介", description(comic))
    }

    @Test
    fun skipsEmptySubtitle() {
        val comic = json.decodeFromJsonElement<THComic>(
            catalogEntry("subtitle" to JsonPrimitive("")),
        )

        assertNull(description(comic))
    }

    @Test
    fun acceptsMissingOptionalSubtitle() {
        val comic = json.decodeFromJsonElement<THComic>(
            JsonObject(catalogEntry() - "subtitle"),
        )

        assertNull(description(comic))
    }

    @Test
    fun decodesTalosCatalog() {
        val comics = json.decodeFromString<THResult<List<THComic>>>(
            fixture("talos-ii-historicus-comics.json"),
        ).data

        assertEquals(6, comics.size)
        assertEquals("6251", comics.first().cid)
    }

    @Test
    fun rejectsNullRequiredTitle() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromJsonElement<THComic>(catalogEntry("title" to JsonNull))
        }
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/$name"))
        .bufferedReader().use { it.readText() }

    private fun catalogEntry(vararg overrides: Pair<String, kotlinx.serialization.json.JsonElement>): JsonObject {
        val entry = json.parseToJsonElement(fixture("terra-historicus-comics.json"))
            .jsonObject.getValue("data").jsonArray.first().jsonObject
        return JsonObject(entry + overrides)
    }

    private fun description(comic: THComic) = THComic::class.java.getDeclaredMethod("getDescription").run {
        isAccessible = true
        invoke(comic)
    }
}
