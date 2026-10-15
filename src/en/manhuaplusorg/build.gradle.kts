import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaPlus (Unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "liliana"

    source {
        lang = "en"
        baseUrl = "https://manhuaplus.org"
    }
}
