plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Clown Corps"
    className = "ClownCorps"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
