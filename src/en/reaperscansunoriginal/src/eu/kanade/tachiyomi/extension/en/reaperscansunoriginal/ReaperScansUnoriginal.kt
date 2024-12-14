package eu.kanade.tachiyomi.extension.en.reaperscansunoriginal

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class ReaperScansUnoriginal : MangaThemesia(
    "Reaper Scans (unoriginal)",
    "https://reaper-scans.com",
    "en",
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()
}
