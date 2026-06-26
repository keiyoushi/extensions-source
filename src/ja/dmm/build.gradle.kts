plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DMM/FANZA"
    className = "DmmFactory"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:publus"))
    implementation(project(":lib:cookieinterceptor"))
}
