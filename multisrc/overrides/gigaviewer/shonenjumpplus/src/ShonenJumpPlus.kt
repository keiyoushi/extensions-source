package eu.kanade.tachiyomi.extension.ja.shonenjumpplus

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import okhttp3.OkHttpClient

class ShonenJumpPlus : GigaViewer(
    "Shonen Jump+",
    "https://shonenjumpplus.com",
    "ja",
    "https://cdn-ak-img.shonenjumpplus.com",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "集英社"

    override fun getCollections(): List<Collection> = listOf(
        Collection("ジャンプ＋連載一覧", ""),
        Collection("ジャンプ＋読切シリーズ", "oneshot"),
        Collection("連載終了作品", "finished"),
    )
}
