plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReiManga"
    className = "Reimanga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
