package eu.kanade.tachiyomi.extension.all.otakusanctuary

import eu.kanade.tachiyomi.multisrc.otakusanctuary.OtakuSanctuary
import eu.kanade.tachiyomi.source.SourceFactory

class OtakuSanctuaryFactory : SourceFactory {
    override fun createSources() = listOf(
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "all"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "vi"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "en"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "it"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "fr"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.net", "es"),
    )
}
