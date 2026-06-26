plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "xkcd"
    className = "XkcdFactory"
    versionCode = 16
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
