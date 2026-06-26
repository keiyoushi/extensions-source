plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushi-Scan"
    className = "SushiScan"
    versionCode = 17
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://sushiscan.net"
}

dependencies {

    implementation(project(":lib:randomua"))
}
