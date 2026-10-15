import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roseveil"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "loneseal"

    source {
        lang = "id"
        baseUrl = "https://roseveil.org"
        versionId = 2
    }
}
