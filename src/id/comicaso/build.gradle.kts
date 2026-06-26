plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicaso"
    className = "Comicaso"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
