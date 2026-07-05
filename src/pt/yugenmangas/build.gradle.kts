plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yugen Mangás"
    versionCode = 52
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        versionId = 2
        baseUrl("https://yugenmangasbr.dxtg.online") {
            withCustom = true
        }
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
