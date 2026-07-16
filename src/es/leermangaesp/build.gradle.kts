import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerMangaEsp"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

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
