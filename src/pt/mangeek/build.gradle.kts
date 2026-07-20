import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManGeek"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangeek.app"
    }

    deeplink {
        path("/manga/..*")
    }
}
