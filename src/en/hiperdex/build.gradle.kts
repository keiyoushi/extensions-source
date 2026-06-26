plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hiperdex"
    className = "Hiperdex"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://hiperdex.com"
}

dependencies {

    implementation(project(":lib:randomua"))
}
