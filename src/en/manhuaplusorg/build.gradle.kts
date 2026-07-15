import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaPlus (Unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "liliana"

    source {
        lang = "en"
        baseUrl = "https://manhuaplus.org"
    }
}
