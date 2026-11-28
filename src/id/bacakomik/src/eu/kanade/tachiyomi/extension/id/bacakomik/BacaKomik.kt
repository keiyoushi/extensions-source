package eu.kanade.tachiyomi.extension.id.bacakomik

import eu.kanade.tachiyomi.multisrc.komikindo.KomikIndo
import keiyoushi.annotation.Source

@Source
abstract class BacaKomik : KomikIndo() {
    override val mangaListPath = "daftar-komik"
}
