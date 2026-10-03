import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Flix"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "MangaFlix"
        lang = "pt-BR"
        baseUrl = "https://mangaflix.net"
    }
}
