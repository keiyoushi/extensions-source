plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Creative Comic Collection"
    className = "Creativecomic"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}
