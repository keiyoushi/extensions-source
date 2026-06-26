plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ohta Web Comic"
    className = "OhtaWebComic"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:speedbinb"))
}
