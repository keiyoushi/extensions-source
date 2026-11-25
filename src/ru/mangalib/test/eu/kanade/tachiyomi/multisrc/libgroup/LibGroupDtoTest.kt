package eu.kanade.tachiyomi.multisrc.libgroup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LibGroupDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses constants with site_ids as both array and object`() {
        val constants = json.decodeFromString<Data<Constants>>(CONSTANTS_JSON).data

        // status: first entry keeps a plain array, second one is serialized as an object
        assertEquals(listOf(1, 2), constants.titleStatuses[0].siteIds)
        assertEquals(listOf(1, 2), constants.titleStatuses[1].siteIds)

        // scanlateStatus: sparse object {"0": 1, "3": 2} must keep values only
        assertEquals(listOf(1, 2), constants.scanlateStatuses[0].siteIds)

        // filters still resolve per site
        assertEquals(2, constants.getScanlateStatuses(2).size)
        assertEquals(2, constants.getTitleStatuses(1).size)

        // image servers are parsed from the same response
        assertEquals("https://compress.example/", constants.getServer(null, 2).url)
        assertEquals("https://main.example/", constants.getServer("main", 1).url)
    }

    @Test
    fun `parses constants with site_ids as plain arrays`() {
        val constants = json.decodeFromString<Data<Constants>>(CONSTANTS_JSON_ARRAYS_ONLY).data

        assertEquals(listOf(1, 2), constants.titleStatuses[1].siteIds)
        assertEquals(listOf(1, 2), constants.scanlateStatuses[0].siteIds)
        assertEquals("https://compress.example/", constants.getServer(null, 2).url)
    }

    private companion object {
        val CONSTANTS_JSON = """
            {
              "data": {
                "ageRestriction": [{"id": 1, "label": "16+", "site_ids": [1]}],
                "format": [{"id": 1, "name": "Манга", "site_ids": [1, 2]}],
                "genres": [{"id": 1, "name": "Экшн", "site_ids": [1]}],
                "imageServers": [
                  {"id": "main", "label": "Первый", "url": "https://main.example/", "site_ids": [1]},
                  {"id": "compress", "label": "Сжатия", "url": "https://compress.example/", "site_ids": [1, 2]}
                ],
                "scanlateStatus": [
                  {"id": 1, "label": "Продолжается", "site_ids": {"0": 1, "3": 2}},
                  {"id": 2, "label": "Выходит", "site_ids": [2]}
                ],
                "status": [
                  {"id": 1, "label": "Онгоинг", "site_ids": [1, 2]},
                  {"id": 4, "label": "Приостановлен", "site_ids": {"0": 1, "1": 2}}
                ],
                "tags": [{"id": 1, "name": "Тег", "site_ids": [2]}],
                "types": [{"id": 1, "label": "Манга", "site_ids": [1]}]
              }
            }
        """.trimIndent()

        val CONSTANTS_JSON_ARRAYS_ONLY = """
            {
              "data": {
                "ageRestriction": [{"id": 1, "label": "16+", "site_ids": [1]}],
                "format": [{"id": 1, "name": "Манга", "site_ids": [1, 2]}],
                "genres": [{"id": 1, "name": "Экшн", "site_ids": [1]}],
                "imageServers": [
                  {"id": "main", "label": "Первый", "url": "https://main.example/", "site_ids": [1]},
                  {"id": "compress", "label": "Сжатия", "url": "https://compress.example/", "site_ids": [1, 2]}
                ],
                "scanlateStatus": [
                  {"id": 1, "label": "Продолжается", "site_ids": [1, 2]},
                  {"id": 2, "label": "Выходит", "site_ids": [2]}
                ],
                "status": [
                  {"id": 1, "label": "Онгоинг", "site_ids": [1, 2]},
                  {"id": 4, "label": "Приостановлен", "site_ids": [1, 2]}
                ],
                "tags": [{"id": 1, "name": "Тег", "site_ids": [2]}],
                "types": [{"id": 1, "label": "Манга", "site_ids": [1]}]
              }
            }
        """.trimIndent()
    }
}
