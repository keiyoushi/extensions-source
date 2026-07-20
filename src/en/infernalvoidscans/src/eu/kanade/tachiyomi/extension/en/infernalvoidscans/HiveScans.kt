package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.CacheControl
import okhttp3.Request

@Source
abstract class HiveScans : Iken() {

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headers,
        CacheControl.Builder().noCache().build(),
    )
}
