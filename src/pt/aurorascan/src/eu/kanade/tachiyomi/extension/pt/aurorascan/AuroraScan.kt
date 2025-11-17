package eu.kanade.tachiyomi.extension.pt.aurorascan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AuroraScan : GreenShit(
    "Aurora Scan",
    "https://www.serenitytoons.win",
    "pt-BR",
) {
    override val apiUrl = "https://api.sussytoons.wtf"
    override val useWidthInThumbnail = false
    override val defaultOrderBy = "data"
    override val targetAudience = TargetAudience.Shoujo
    override val popularType = "periodo"
    override val popularTypeValue = "geral"
    override val latestEndpoint = "novos-capitulos"
    override val includeSlugInUrl = true
    override val defaultScanId = 4

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "serenitytoons.win")

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun getFilterList() = FilterList()
}
