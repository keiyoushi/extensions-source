package eu.kanade.tachiyomi.extension.en.nyxscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Response

class NyxScans : Iken(
    "Nyx Scans",
    "en",
    "https://nyxscans.com",
    "https://api.nyxscans.com",
) {
    // ============================== Popular ===============================

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.asJsoup().getNextJson("popularPosts")

        val entries = data.parseAs<List<PopularParseDto>>().map { entry ->
            SManga.create().apply {
                title = entry.postTitle
                thumbnail_url = entry.featuredImage
                url = "${entry.slug}#${entry.id}"
            }
        }

        return MangasPage(entries, false)
    }

    @Serializable
    class PopularParseDto(
        val id: Int,
        val slug: String,
        val postTitle: String,
        val featuredImage: String? = null,
    )
}
