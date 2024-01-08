package eu.kanade.tachiyomi.extension.zh.manhuawu

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MangaDto

class Manhuawu : MCCMS("漫画屋", "https://www.mhua5.com", hasCategoryPage = true) {

    override fun MangaDto.prepare() = copy(url = "/comic-$id.html")

    override fun getMangaId(url: String) = url.substringAfterLast('-').substringBeforeLast('.')
}
