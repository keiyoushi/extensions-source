import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerMangaEsp"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://mangalect.org"
    }

    deeplink {
        host("mangalect.org")
        path("/manga/..*")
        path("/leer-m/..*")
    }
}
