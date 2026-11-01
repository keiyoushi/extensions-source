import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiVNx"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.hentaivnx.com")
        }
    }

    deeplink {
        path("/truyen-hentai/..*")
    }
}
