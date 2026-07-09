plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushi-Scan"
    versionCode = 17
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://sushiscan.net"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
