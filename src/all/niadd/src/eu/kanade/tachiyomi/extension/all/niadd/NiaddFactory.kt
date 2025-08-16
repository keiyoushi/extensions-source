package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddEn(),    // Inglês - www.niadd.com
        NiaddPtBr(),  // Português - br.niadd.com
        NiaddDe(),    // Alemão - de.niadd.com
        NiaddEs(),    // Espanhol - es.niadd.com
        NiaddFr(),    // Francês - fr.niadd.com
        NiaddIt(),    // Italiano - it.niadd.com
        NiaddRu(),    // Russo - ru.niadd.com
    )
}
