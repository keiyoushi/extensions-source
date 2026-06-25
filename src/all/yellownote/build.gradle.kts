plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YellowNote"
    className = "YellowNote"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:i18n"))
}
