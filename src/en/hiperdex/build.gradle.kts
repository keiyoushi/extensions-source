plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hiperdex"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl {
            custom("https://hiperdex.com")
        }
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
