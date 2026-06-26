plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas.in"
    className = "MangasIn"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mmrcms"
    baseUrl = "https://m440.in"
}

dependencies {

    implementation(project(":lib:synchrony"))
    implementation(project(":lib:cryptoaes"))
}
