import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Siikomik"
    versionCode = 62
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://siikomik.com"
        versionId = 3
    }

    deeplink {
        host("siikomik.com")
        host("siikomik.id")
        path("/komik/..*")
    }
}
