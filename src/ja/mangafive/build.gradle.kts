plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-5"
    className = "MangaFive"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
}

dependencies {
    implementation(project(":lib:publus"))
}
