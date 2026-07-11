import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas No Sekai"
    versionCode = 19
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://mangasnosekai.com"
    }
}

dependencies {

    implementation(project(":lib:synchrony"))
}
