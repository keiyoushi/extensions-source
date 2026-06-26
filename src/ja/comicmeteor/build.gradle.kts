plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiraboshi"
    className = "ComicMeteor"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
}
