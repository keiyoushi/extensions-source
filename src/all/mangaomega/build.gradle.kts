import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Omega"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://mangaomega.com"
        }
    }

    deeplink {
        path("/..*")
    }
}
