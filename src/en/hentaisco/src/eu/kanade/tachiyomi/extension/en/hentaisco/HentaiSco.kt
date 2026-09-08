package eu.kanade.tachiyomi.extension.en.hentaisco

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class HentaiSco : MadaraNoAjax() {
    override val mangaSubString = "hentai"
}
