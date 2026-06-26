plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Honeytoon"
    className = "HoneytoonFactory"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:i18n"))
}
