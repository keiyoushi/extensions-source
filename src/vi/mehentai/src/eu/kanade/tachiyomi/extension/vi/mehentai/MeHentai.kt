package eu.kanade.tachiyomi.extension.vi.mehentai

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import keiyoushi.annotation.Source

@Source
abstract class MeHentai : ManhwaZ() {

    override val mangaDetailsAuthorHeading = "Tác giả"

    override val mangaDetailsStatusHeading = "Trạng thái"

    override val searchPath = "tim-kiem"
}
