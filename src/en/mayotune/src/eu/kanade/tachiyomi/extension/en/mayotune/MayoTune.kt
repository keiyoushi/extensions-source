package eu.kanade.tachiyomi.extension.en.mayotune

import eu.kanade.tachiyomi.multisrc.mayotune.MayoTune
import eu.kanade.tachiyomi.source.model.SManga

class MayoTune : MayoTune("MayoTune", "https://mayotune.xyz/", "en") {
    override val sourceList = listOf(
        SManga.create().apply {
            title = "Mayonaka Heart Tune"
            url = baseUrl
            thumbnail_url = "${baseUrl}img/cover.jpg"
            author = "Masakuni Igarashi"
            artist = "Masakuni Igarashi"
        },
    )
}
