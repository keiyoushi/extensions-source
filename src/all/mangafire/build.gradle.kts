import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaFire"
    versionCode = 26
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf("en", "es", "es-419", "fr", "ja", "pt", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://mangafire.to"
        }
    }
}
