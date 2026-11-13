import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas Sin Censura"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://mangasincensura.com"
    }

    source {
        name = "Uncensored Manga"
        lang = "en"
        baseUrl = "https://uncensoredmanga.com"
    }

    deeplink {
        path("/manhwa_index")
        path("/chapter")
    }
}
