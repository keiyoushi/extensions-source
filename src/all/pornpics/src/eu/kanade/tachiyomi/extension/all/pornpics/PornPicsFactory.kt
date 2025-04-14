package eu.kanade.tachiyomi.extension.all.pornpics

import eu.kanade.tachiyomi.source.SourceFactory

class PornPicsFactory : SourceFactory {

    override fun createSources() = listOf(
        PornPics("all"),
        PornPics("en"),
        PornPics("zh"),
    )
}
