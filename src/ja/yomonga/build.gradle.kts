plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yomonga"
    className = "Yomonga"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
}
