import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "West Manga"
    versionCode = 44
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://v1.westmanga.my"
        id = 8883916630998758688L
    }
}
