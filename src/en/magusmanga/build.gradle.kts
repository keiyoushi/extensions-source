import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Magus Manga"
    versionCode = 46
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://magustoon.org"
        lang = "en"
        // Moved from Keyoapp to Iken
        versionId = 3
    }
}
