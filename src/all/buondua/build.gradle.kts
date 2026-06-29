plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Buon Dua"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://buondua.com"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
