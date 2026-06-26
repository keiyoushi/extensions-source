plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga TV"
    className = "MangaTV"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://mangatv.net"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
