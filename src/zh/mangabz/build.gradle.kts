plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabz"
    className = "Mangabz"
    versionCode = 14
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:unpacker"))
}
