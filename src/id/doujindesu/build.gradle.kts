plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinDesu"
    className = "DoujinDesu"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
    implementation(project(":lib:cookieinterceptor"))
}
