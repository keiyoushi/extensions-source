import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Bahçesi"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://mangabahcesi.com"
        id = 7110025728969951060L
    }

    deeplink {
        path("/Tr/Manga/..*")
    }
}
