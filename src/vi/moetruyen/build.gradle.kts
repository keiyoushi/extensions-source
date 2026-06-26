plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoeTruyen"
    className = "MoeTruyen"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
