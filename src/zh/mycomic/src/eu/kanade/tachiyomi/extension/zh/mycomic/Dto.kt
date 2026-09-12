package eu.kanade.tachiyomi.extension.zh.mycomic

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    private val id: Long,
    private val title: String,
) {
    fun toSChapter(dateUpload: Long, scanlator: String?) = SChapter.create().apply {
        name = title
        url = "/chapters/$id"
        date_upload = dateUpload
        this.scanlator = scanlator
    }
}
