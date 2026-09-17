package eu.kanade.tachiyomi.extension.en.suryascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class GenzToons : Keyoapp() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        connectTimeout(90.seconds)
        writeTimeout(90.seconds)
        readTimeout(90.seconds)
        rateLimit(3)
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())
}
