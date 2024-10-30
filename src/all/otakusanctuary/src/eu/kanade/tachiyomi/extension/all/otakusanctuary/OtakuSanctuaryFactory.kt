package eu.kanade.tachiyomi.extension.all.otakusanctuary

import eu.kanade.tachiyomi.multisrc.otakusanctuary.OtakuSanctuary
import eu.kanade.tachiyomi.source.SourceFactory

class OtakuSanctuaryFactory : SourceFactory {
    override fun createSources() = listOf(
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "all"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "vi"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "en"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "it"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "fr"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan1.net", "es"),
    )
}
