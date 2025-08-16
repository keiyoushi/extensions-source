package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddEn(), 
        NiaddPtBr(), 
        NiaddDe(),  
        NiaddEs(),  
        NiaddFr(),  
        NiaddIt(), 
        NiaddRu(),
    )
}
