package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class EZmanga :
    Iken(
        "EZmanga",
        "en",
        "https://ezmanga.org",
        "https://vapi.ezmanga.org",
    ) {
    // Migrated from HeanCms to Iken
    override val versionId = 4

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val sortOptions =
        listOf(
            Pair("Latest", "updatedAt"),
            Pair("Popularity", "totalViews"),
            Pair("Created at", "createdAt"),
            Pair("Z-A", "postTitle"),
        )
}
