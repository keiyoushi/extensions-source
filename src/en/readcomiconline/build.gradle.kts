plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadComicOnline"
    className = "Readcomiconline"
    versionCode = 43
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}
