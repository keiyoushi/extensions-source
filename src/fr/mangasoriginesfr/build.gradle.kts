import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas-Origines.fr"
    versionCode = 57
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "origines"

    source {
        lang = "fr"
        baseUrl = "https://mangas-origines.fr"
    }
}
