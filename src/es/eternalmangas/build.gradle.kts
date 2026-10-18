import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EternalMangas"
    versionCode = 30
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://eternalmangas.org"
        lang = "es"
        versionId = 2
    }

    deeplink {
        path("/series/..*")
    }
}
