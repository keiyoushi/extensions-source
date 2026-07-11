import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Elder Manga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://eldermanga.com"
        versionId = 2
    }
}
