package eu.kanade.tachiyomi.extension.pt.nextscan

import eu.kanade.tachiyomi.multisrc.etoshore.Etoshore
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response

class NextScan : Etoshore(
    "Next Scan",
    "https://nextscan.cloud",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).reversed()
}
