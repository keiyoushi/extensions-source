import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komikzoid"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "colorlibanime"

    source {
        lang = "id"
        baseUrl = "https://01.komikzoid.id"
    }
}
