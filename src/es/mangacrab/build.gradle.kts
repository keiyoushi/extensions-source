plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Crab"
    className = "MangaCrab"
    versionCode = 23
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://mangacrab.org"
}

dependencies {

    implementation(project(":lib:randomua"))
}
