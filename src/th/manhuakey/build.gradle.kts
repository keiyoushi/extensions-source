plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaKey"
    className = "ManhuaKey"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://www.manhuakey.com"
}

dependencies {

    implementation(project(":lib:unpacker"))
}
