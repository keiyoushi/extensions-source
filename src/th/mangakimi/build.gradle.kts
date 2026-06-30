plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKimi"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://www.mangakimi.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
