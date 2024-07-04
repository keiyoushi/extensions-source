package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Siikomik : MangaThemesia(
    "Siikomik",
    "https://siikomik.com",
    "id",
) {
    override val id = 5693774260946188681

    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
