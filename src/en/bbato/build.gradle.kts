import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bbato"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            custom("https://bato1.co.uk")
        }
    }

    deeplink {
        host("bato1.co.uk")
        host("bato1.com")
        host("bbato.com")
        host("xbato.co.uk")
        host("www.xbato.co.uk")
        path("/manga/..*")
        path("/read/..*")
    }

    deeplink {
        host("mangabuddy1.co.uk")
        host("www.mangabuddy1.co.uk")
        path("/series/..*")
    }
}
