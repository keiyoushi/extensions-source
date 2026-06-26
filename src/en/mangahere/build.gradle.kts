plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahere"
    className = "Mangahere"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
