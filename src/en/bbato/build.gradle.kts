import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bbato"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            custom("https://bato1.com")
        }
    }

    deeplink {
        host("bato1.com")
        host("bbato.com")
        path("/manga/..*")
        path("/read/..*")
    }
}
