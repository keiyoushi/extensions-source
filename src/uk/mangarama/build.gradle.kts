import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangarama"
    pkgName = "uk.pureskill"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "uk"
        baseUrl = "https://mangarama.com.ua"
    }

    deeplink {
        path("/manga/..*")
    }
}
