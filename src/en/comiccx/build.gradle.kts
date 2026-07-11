plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic CX"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comic.cx"
    }
}

dependencies {

    implementation(project(":lib:dataimage"))
}
