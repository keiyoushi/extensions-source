package eu.kanade.tachiyomi.extension.ar.crowscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class Hadess : Madara(
    "Hadess",
    "https://www.hadess.xyz",
    "ar",
    dateFormat = SimpleDateFormat("dd MMMM، yyyy", Locale("ar")),
) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorStatus =
        ".summary-heading:contains(الحالة) + ${super.mangaDetailsSelectorStatus}"
}
