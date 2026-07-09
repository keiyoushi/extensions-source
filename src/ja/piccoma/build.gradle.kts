plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Piccoma"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://piccoma.com"
    }
}

dependencies {

    implementation(project(":lib:seedrandom"))
}
