package eu.kanade.tachiyomi.extension.vi.sayhentai

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ

class SayHentai : ManhwaZ(
    "SayHentai",
    "https://sayhentai.art",
    "vi",
    mangaDetailsAuthorHeading = "Tác giả",
    mangaDetailsStatusHeading = "Trạng thái",
) {
    override fun popularMangaSelector() = "#slide-top > .item:contains(a)"
}
