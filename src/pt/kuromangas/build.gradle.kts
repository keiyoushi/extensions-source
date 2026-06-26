plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KuroMangas"
    className = "KuroMangas"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
