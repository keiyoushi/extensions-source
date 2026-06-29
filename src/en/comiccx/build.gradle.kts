plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic CX"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comic.cx"
    }
}

dependencies {

    implementation(project(":lib:dataimage"))
}
