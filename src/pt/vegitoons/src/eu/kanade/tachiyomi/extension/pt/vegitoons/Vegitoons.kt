package eu.kanade.tachiyomi.extension.pt.vegitoons

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source

@Source
abstract class Vegitoons : GreenShit() {
    override val apiUrl = "https://api.vegitoons.black"
    override val cdnUrl = "https://cdn.vegitoons.black"
    override val cdnApiUrl = "https://api.vegitoons.black/cdn"
    override val scanId = "1"

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"
}
