package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.extension.all.niadd.NiaddDe
import eu.kanade.tachiyomi.extension.all.niadd.NiaddEn
import eu.kanade.tachiyomi.extension.all.niadd.NiaddEs
import eu.kanade.tachiyomi.extension.all.niadd.NiaddFr
import eu.kanade.tachiyomi.extension.all.niadd.NiaddIt
import eu.kanade.tachiyomi.extension.all.niadd.NiaddPtBr
import eu.kanade.tachiyomi.extension.all.niadd.NiaddRu

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
