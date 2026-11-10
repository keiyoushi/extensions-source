import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Plumics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("fr", "en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://plumics.com"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/comics/..*")
        path("/webtoon/..*")
        path("/bd/..*")
        path("/reader/..*")
    }
}
