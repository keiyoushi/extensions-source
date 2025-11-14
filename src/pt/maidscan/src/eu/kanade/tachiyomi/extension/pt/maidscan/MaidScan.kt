package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.Format
import eu.kanade.tachiyomi.multisrc.greenshit.Genre
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit.TargetAudience
import eu.kanade.tachiyomi.multisrc.greenshit.ResultDto
import eu.kanade.tachiyomi.multisrc.greenshit.Status
import eu.kanade.tachiyomi.multisrc.greenshit.Tag
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MaidScan : GreenShit(
    "Maid Scan",
    "https://empreguetes.xyz",
    "pt-BR",
) {
    override val apiUrl = "https://api.sussytoons.wtf"
    override val cdnUrl = "https://cdn.sussytoons.wtf"
    override val useWidthInThumbnail = false
    override val defaultOrderBy = "data"
    override val targetAudience = TargetAudience.Shoujo
    override val popularGenreId = "4"
    override val latestGenreId = "4"
    override val popularType = "periodo"
    override val popularTypeValue = "geral"
    override val latestEndpoint = "novos-capitulos"
    override val includeSlugInUrl = true
    override val genreFilterKey = "generos"
    override val formatFilterKey = "formatos"
    override val statusFilterKey = "status"
    override val tagFilterKey = "tags"
    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "empreguetes.xyz")

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    private val genresCache by lazy { getFilterList<Genre>("generos") }
    private val formatsCache by lazy { getFilterList<Format>("formatos") }
    private val statusesCache by lazy { getFilterList<Status>("status") }
    private val tagsCache by lazy { getFilterList<Tag>("tags", 2000) }

    private inline fun <reified T> getFilterList(endpoint: String, limit: Int = 100): List<T> =
        runCatching {
            runBlocking(Dispatchers.IO) {
                client.newCall(GET("$apiUrl/$endpoint?limite=$limit", headers)).execute()
                    .parseAs<ResultDto<List<T>>>().results
            }
        }.getOrDefault(emptyList())

    override fun getGenres(): List<Genre> = genresCache.takeIf { it.isNotEmpty() }
        ?: listOf(Genre(0, "$FILTER_FALLBACK_MESSAGE os gêneros"))

    override fun getFormats(): List<Format> = formatsCache.takeIf { it.isNotEmpty() }
        ?: listOf(Format(0, "$FILTER_FALLBACK_MESSAGE os formatos"))

    override fun getStatuses(): List<Status> = statusesCache.takeIf { it.isNotEmpty() }
        ?: listOf(Status(0, "$FILTER_FALLBACK_MESSAGE os status"))

    override fun getTags(): List<TagCheckBox> = tagsCache.takeIf { it.isNotEmpty() }
        ?.map { TagCheckBox(it.name, it.id.toString()) }
        ?: listOf(TagCheckBox("$FILTER_FALLBACK_MESSAGE as tags", ""))

    companion object {
        private const val FILTER_FALLBACK_MESSAGE = "Clique em 'Filtrar' depois 'Redefinir' para carregar"
    }
}
