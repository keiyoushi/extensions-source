package eu.kanade.tachiyomi.extension.vi.umetruyen

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import keiyoushi.annotation.Source

@Source
abstract class UmeTruyen : ManhwaZ() {

    override val mangaDetailsAuthorHeading = "Tác giả"

    override val mangaDetailsStatusHeading = "Trạng thái"
}
