plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorManga.lat"
    className = "LectorMangaLat"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://lectormangass.com"
}

dependencies {

    implementation(project(":lib:randomua"))
}
