plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    className = "MangaBallFactory"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
