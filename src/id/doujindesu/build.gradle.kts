plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinDesu"
    className = "DoujinDesu"
    versionCode = 15
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
    implementation(project(":lib:cookieinterceptor"))
}
