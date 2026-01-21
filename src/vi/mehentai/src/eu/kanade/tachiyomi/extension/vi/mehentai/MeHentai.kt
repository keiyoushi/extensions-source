package eu.kanade.tachiyomi.extension.vi.mehentai

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ

class MeHentai : ManhwaZ(
    "MeHentai",
    "https://mehentai.pro",
    "vi",
    mangaDetailsAuthorHeading = "Tác giả",
    mangaDetailsStatusHeading = "Trạng thái",
) {
    override val searchPath = "tim-kiem"
}
