package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.multisrc.mangotheme.MangoTheme
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers

class ImperioDaBritannia : MangoTheme() {

    override val name = "Sagrado Imp\u00e9rio da Britannia"

    override val baseUrl = "https://imperiodabritannia.net"

    override val lang = "pt-BR"

    override val cdnUrl = "https://cdn.imperiodabritannia.net"

    override val encryptionKey = "mangotoons_encryption_key_2025"

    override val webMangaPathSegment = "manga"

    override val apiUrl = "https://api.${baseUrl.substringAfterLast("/")}/api"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("X-API-Token", apiToken)
        .set("X-Brit-Cache", "true")
        .set("X-Noencryptionbritta", "1")

    private val apiToken: String by lazy {
        val url = client.newCall(GET(baseUrl)).execute().asJsoup()
            .selectFirst("link[href*=env]")
            ?.absUrl("href") ?: return@lazy ""
        API_TOKEN_REGEX.find(client.newCall(GET(url)).execute().body.string())?.groupValues?.last() ?: ""
    }

    override fun getStatusFilterOptions() = ImperioDaBritanniaFilters.statusOptions

    override fun getFormatFilterOptions() = ImperioDaBritanniaFilters.formatOptions

    override fun getTagFilterOptions() = ImperioDaBritanniaFilters.tagOptions

    companion object {
        private val API_TOKEN_REGEX = """apiToken:.([^`]+)""".toRegex()
    }
}
