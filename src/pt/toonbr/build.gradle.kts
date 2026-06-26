plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonBr"
    className = "ToonBr"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
