import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaVault"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://mangavault.lat"
    }

    deeplink {
        host("mangavault.lat")
        host("www.mangavault.lat")
        path("/manga/..*")
    }
}
