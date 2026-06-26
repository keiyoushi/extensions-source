plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerCapitulo"
    className = "LeerCapitulo"
    versionCode = 17
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:synchrony"))
}
