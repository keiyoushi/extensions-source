import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Stop"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangastop.net"
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
