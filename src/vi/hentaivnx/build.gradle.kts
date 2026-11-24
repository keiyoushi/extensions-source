import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiVNx"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.hentaivnx.com")
        }
    }

    deeplink {
        host("www.hentaivnx.com")
        host("hentaivnx.com")
        host("www.hentaivn.college")
        host("hentaivn.college")
        path("/truyen-hentai/..*")
    }
}
