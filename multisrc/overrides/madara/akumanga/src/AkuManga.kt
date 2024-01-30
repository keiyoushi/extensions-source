package eu.kanade.tachiyomi.extension.en.akumanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AkuManga : Madara("AkuManga", "https://akumanga.com", "en") {

    // "AkuManga/ar/1" - override file was misplaced so this ID wasn't retained
    // override val id: Long = 107810123708352143

    override val chapterUrlSuffix = ""
}
