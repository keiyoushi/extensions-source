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
        baseUrl("https://hiperdex.com") {
            withCustom = true
        }
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
