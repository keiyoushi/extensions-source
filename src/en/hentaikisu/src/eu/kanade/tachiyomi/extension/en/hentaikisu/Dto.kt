package eu.kanade.tachiyomi.extension.en.hentaikisu

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    private val id: String,
    private val title: String,
    private val img: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        this.title = this@Dto.title
        thumbnail_url = img
    }
}
