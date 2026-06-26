plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Weekly Young Magazine"
    className = "YanmagaFactory"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
}
