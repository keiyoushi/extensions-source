package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.Format
import eu.kanade.tachiyomi.multisrc.greenshit.Genre
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit.TargetAudience
import eu.kanade.tachiyomi.multisrc.greenshit.ResultDto
import eu.kanade.tachiyomi.multisrc.greenshit.Status
import eu.kanade.tachiyomi.multisrc.greenshit.Tag
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MaidScan : GreenShit(
    "Maid Scan",
    "https://empreguetes.xyz",
    "pt-BR",
) {
    override val apiUrl = "https://api.sussytoons.wtf"
    override val defaultOrderBy = "data"
    override val targetAudience = TargetAudience.Shoujo
    override val popularGenreId = "4"
    override val latestGenreId = "4"
    override val popularType = "periodo"
    override val popularTypeValue = "geral"
    override val latestEndpoint = "novos-capitulos"
    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "empreguetes.xyz")

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .rateLimit(2)
        .build()

    private inline fun <reified T> getFilterList(endpoint: String, limit: Int = 100): List<T> = runBlocking {
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(GET("$apiUrl/$endpoint?limite=$limit", headers)).execute()
                    .parseAs<ResultDto<List<T>>>().results
            }.getOrDefault(emptyList())
        }
    }

    override fun getGenres(): List<Genre> = getFilterList("generos")

    override fun getFormats(): List<Format> = getFilterList("formatos")

    override fun getStatuses(): List<Status> = getFilterList("status")

    override fun getTags(): List<TagCheckBox> = getFilterList<Tag>("tags", 2000).map { TagCheckBox(it.name, it.id.toString()) }
}
