plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "J-Novel"
    className = "JNovel"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:e4p"))
}
