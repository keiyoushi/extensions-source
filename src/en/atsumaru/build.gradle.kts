import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Atsumaru"
    versionCode = 23
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://atsu.moe"
        versionId = 2
    }

    deeplink {
        path("/manga/..*")
    }
}
