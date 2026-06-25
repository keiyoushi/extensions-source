plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDex"
    className = "MangaDexFactory"
    versionCode = 210
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:i18n"))
}
