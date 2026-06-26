plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KissLove"
    className = "KissLove"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    api(project(":lib:i18n"))
}
