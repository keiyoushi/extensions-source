package eu.kanade.tachiyomi.extension.es.codexzero

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class CodexZero :
    Madara(
        "Codex Zero",
        "https://codex.readkisho.me",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
