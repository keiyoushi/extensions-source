import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonGod"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.toongod.org"
    }
}
