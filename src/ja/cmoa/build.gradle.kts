plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "C'moA"
    className = "Cmoa"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
    implementation(project(":lib:cookieinterceptor"))
}
