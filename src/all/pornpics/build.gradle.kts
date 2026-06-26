plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PornPics"
    className = "PornPicsFactory"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:i18n"))
}
