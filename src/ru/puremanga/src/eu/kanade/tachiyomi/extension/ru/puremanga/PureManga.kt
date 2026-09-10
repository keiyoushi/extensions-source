package eu.kanade.tachiyomi.extension.ru.puremanga

import eu.kanade.tachiyomi.multisrc.inkstory.InkStory
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class PureManga : InkStory() {
    private val domain = baseUrl.toHttpUrl().topPrivateDomain() ?: baseUrl.toHttpUrl().host
    override val apiUrl: String
        get() = "https://api.$domain/v2"
}
