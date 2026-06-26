plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Grast"
    className = "ComicGrast"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:seedrandom"))
}
