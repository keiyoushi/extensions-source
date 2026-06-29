package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source

@Source
abstract class HiveScans : Iken() {
    override val client =
        super.client
            .newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val headers =
                    request.headers
                        .newBuilder()
                        .set("Cache-Control", "max-age=0")
                        .build()
                chain.proceed(request.newBuilder().headers(headers).build())
            }.build()

    override fun headersBuilder() = super
        .headersBuilder()
        .set("Cache-Control", "max-age=0")
}
