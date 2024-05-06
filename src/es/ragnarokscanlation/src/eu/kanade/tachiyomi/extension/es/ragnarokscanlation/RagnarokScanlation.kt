package eu.kanade.tachiyomi.extension.es.ragnarokscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RagnarokScanlation : Madara(
    "Ragnarok Scanlation",
    "https://ragnarokscanlation.org",
    "es",
    SimpleDateFormat("MMMM d, yyyy", Locale("en")),
) {
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaSubString = "series"
}
