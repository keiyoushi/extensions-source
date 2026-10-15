import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLib"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://mangalib.me")
        }
        lang = "ru"
    }
}
