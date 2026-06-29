plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Swords Comic"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://swordscomic.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
