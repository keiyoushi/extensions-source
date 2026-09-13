import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Slept Manga"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://sleptmanga.com.tr"
    }

    deeplink {
        path("/manhwa/..*")
        path("/manga/..*")
        path("/manhua/..*")
    }
}
