plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaKey"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://www.manhuakey.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}
