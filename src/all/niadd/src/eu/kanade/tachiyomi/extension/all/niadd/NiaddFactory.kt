package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.extension.de.niadd.NiaddDe
import eu.kanade.tachiyomi.extension.en.niadd.NiaddEn
import eu.kanade.tachiyomi.extension.fr.niadd.NiaddFr
import eu.kanade.tachiyomi.extension.it.niadd.NiaddIt
import eu.kanade.tachiyomi.extension.pt.niadd.NiaddPtBr
import eu.kanade.tachiyomi.extension.es.niadd.NiaddEs
import eu.kanade.tachiyomi.extension.ru.niadd.NiaddRu

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddDe(),
        NiaddEn(),
        NiaddEs(),
        NiaddFr(),
        NiaddIt(),
        NiaddPtBr(),
        NiaddRu(),
    )
}
