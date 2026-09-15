import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebtoonOku"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://webtoonoku.org"
    }

    deeplink {
        path("/manhwa/..*")
        path("/..*-chapter-..*")
    }
}
