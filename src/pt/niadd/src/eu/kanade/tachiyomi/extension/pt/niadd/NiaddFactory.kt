package eu.kanade.tachiyomi.extension.pt.niadd

import eu.kanade.tachiyomi.extension.pt.niadd.NiaddPtBr
import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddPtBr()
    )
}
