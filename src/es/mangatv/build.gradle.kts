import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga TV"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "Manga  TV"
        lang = "es"
        baseUrl = "https://mangatv.net"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
