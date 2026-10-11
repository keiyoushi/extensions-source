import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VinaHentai"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://vinahentai.pics")
        }
    }

    deeplink {
        path("/truyen-hentai/..*")
    }
}
