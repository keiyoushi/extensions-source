package eu.kanade.tachiyomi.extension.all.leagueoflegends

import eu.kanade.tachiyomi.source.SourceFactory

class LOLFactory : SourceFactory {
    override fun createSources() = listOf(
        LOLUniverse("en_us"),
        // LOLUniverse("en_gb"),
        LOLUniverse("de_de"),
        LOLUniverse("es_es"),
        LOLUniverse("fr_fr"),
        LOLUniverse("it_it"),
        // LOLUniverse("en_pl"),
        LOLUniverse("pl_pl"),
        LOLUniverse("el_gr"),
        LOLUniverse("ro_ro"),
        LOLUniverse("hu_hu"),
        LOLUniverse("cs_cz"),
        LOLUniverse("es_mx", "es-419"),
        // LOLUniverse("es_ar", "es-419"),
        LOLUniverse("pt_br", "pt-BR"),
        LOLUniverse("ja_jp"),
        LOLUniverse("ru_ru"),
        LOLUniverse("tr_tr"),
        // LOLUniverse("en_au"),
        LOLUniverse("ko_kr"),
    )
}
