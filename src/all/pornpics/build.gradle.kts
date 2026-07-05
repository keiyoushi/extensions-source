plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PornPics"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "zh").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://www.pornpics.com"
            if (langCode == "en") id = 1459635082044256286L
        }
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
