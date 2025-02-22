package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaToonFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        MangaToonZh(),
        MangaToonEn(),
        MangaToonId(),
        MangaToonVi(),
        MangaToonEs(),
        MangaToonPt(),
        MangaToonTh(),
        MangaToonFr(),
        MangaToonJa(),
    )
}

class MangaToonZh : MangaToon("zh", "cn")
class MangaToonEn : MangaToon("en")
class MangaToonId : MangaToon("id")
class MangaToonVi : MangaToon("vi")
class MangaToonEs : MangaToon("es")
class MangaToonPt : MangaToon("pt-BR", "pt")
class MangaToonTh : MangaToon("th")
class MangaToonFr : MangaToon("fr")
class MangaToonJa : MangaToon("ja")
