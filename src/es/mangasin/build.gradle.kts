plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas.in"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mmrcms"

    source {
        lang = "es"
        baseUrl = "https://m440.in"
    }
}

dependencies {

    implementation(project(":lib:synchrony"))
    implementation(project(":lib:cryptoaes"))
}
