import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikigai Mangas"
    versionCode = 40
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl {
            custom("https://visorikigai.gettocaboca.com")
        }
        versionId = 2
    }
}
