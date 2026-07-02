package eu.kanade.tachiyomi.extension.vi.sayhentai

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import keiyoushi.annotation.Source

@Source
abstract class SayHentai : ManhwaZ() {

    override val mangaDetailsAuthorHeading = "Tác giả"

    override val mangaDetailsStatusHeading = "Trạng thái"

    override fun popularMangaSelector() = "#slide-top > .item:contains(a)"
}
