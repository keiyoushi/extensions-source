plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKimi"
    className = "MangaKimi"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://www.mangakimi.com"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
