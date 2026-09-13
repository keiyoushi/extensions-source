import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComixTopia"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "ComixTopia"
        lang = "uk"
        baseUrl = "https://comixtopia.in.ua"
    }

    deeplink {
        path("/titles/..*")
    }
}
