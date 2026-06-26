plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Coolmic"
    className = "Coolmic"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
