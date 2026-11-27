import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

val languages = listOf("ar", "de", "en", "es", "fr", "id", "pt-BR", "ru")

keiyoushi {
    name = "Manga Club"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangaverse"

    languages.forEach {
        source {
            lang = it
            baseUrl = "https://www.mangaclub.net"
        }
    }

    deeplink {
        host("www.mangaclub.net")
        host("mangaclub.net")
        languages.forEach { path("/${it.lowercase()}/..*/..*") }
    }
}
