plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas No Sekai"
    className = "MangasNoSekai"
    versionCode = 19
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://mangasnosekai.com"
}

dependencies {

    implementation(project(":lib:synchrony"))
}
