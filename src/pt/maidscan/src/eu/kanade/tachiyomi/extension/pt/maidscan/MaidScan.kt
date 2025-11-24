package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MaidScan : GreenShit(
    "Maid Scan",
    "https://empreguetes.xyz",
    "pt-BR",
) {
    override val useWidthInThumbnail = false
    override val defaultOrderBy = "data"
    override val targetAudience = TargetAudience.Shoujo
    override val popularGenreId = "4"
    override val latestGenreId = "4"
    override val popularType = "periodo"
    override val popularTypeValue = "geral"
    override val latestEndpoint = "atualizacoes"
    override val includeSlugInUrl = true

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "empreguetes.xyz")

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun getFilterList() = FilterList()
}
