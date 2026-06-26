plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangago"
    className = "Mangago"
    versionCode = 34
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
