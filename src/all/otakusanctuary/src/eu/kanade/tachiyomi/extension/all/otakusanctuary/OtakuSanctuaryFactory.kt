package eu.kanade.tachiyomi.extension.all.otakusanctuary

import eu.kanade.tachiyomi.multisrc.otakusanctuary.OtakuSanctuary
import eu.kanade.tachiyomi.source.SourceFactory

class OtakuSanctuaryFactory : SourceFactory {
    override fun createSources() = listOf(
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "all"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "vi"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "en"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "it"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "fr"),
        OtakuSanctuary("Otaku Sanctuary", "https://otakusan.me", "es"),
    )
}
