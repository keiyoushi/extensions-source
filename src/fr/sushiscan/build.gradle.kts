import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushi-Scan"
    versionCode = 17
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://sushiscan.net"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
