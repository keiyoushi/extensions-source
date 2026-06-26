plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dm5"
    className = "Dm5"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
