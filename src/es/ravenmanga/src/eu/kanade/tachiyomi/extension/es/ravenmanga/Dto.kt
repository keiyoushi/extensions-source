package eu.kanade.tachiyomi.extension.es.ravenmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("nombre") val title: String,
    private val slug: String,
    @SerialName("portada") private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@Dto.title
        thumbnail_url = thumbnail
        url = "/sr2/$slug"
    }
}
