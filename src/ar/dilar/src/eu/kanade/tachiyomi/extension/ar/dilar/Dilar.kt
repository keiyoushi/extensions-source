package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response

class Dilar : Gmanga(
    "Dilar",
    "https://dilar.tube",
    "ar",
) {
    override fun chapterListParse(response: Response): List<SChapter> {
        val releases = response.parseAs<ChapterListDto>().releases
            .filterNot { it.has_rev_link && it.support_link.isNotEmpty() }

        return releases.map {
            it.toSChapter(dateFormat)
        }.sortedWith(
            compareBy(
                { -it.chapter_number },
                { -it.date_upload },
            ),
        )
    }
}
