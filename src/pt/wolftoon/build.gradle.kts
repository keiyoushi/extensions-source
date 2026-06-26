plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolftoon"
    className = "Wolftoon"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
