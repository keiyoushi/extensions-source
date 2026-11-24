import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Maid Sama Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "solomanga"

    source {
        lang = "en"
        baseUrl = "https://w14.maid-sama.com"
    }
}
