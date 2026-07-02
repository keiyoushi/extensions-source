plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YellowNote"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://xchina.co"
        id = 170542391855030753L
        skipCodeGen = true
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
