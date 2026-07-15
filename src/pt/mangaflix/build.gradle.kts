import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Flix"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "MangaFlix"
        lang = "pt-BR"
        baseUrl = "https://mangaflix.net"
    }
}
