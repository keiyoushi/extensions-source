plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ameba Manga"
    className = "AmebaManga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
