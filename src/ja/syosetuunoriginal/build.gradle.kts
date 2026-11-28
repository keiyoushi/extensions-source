import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Syosetu (Unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zing"

    source {
        lang = "ja"
        baseUrl {
            custom("https://syosetu.toys")
        }
    }

    deeplink {
        path("/manga/..*")
        path("/manga-genre/..*")
    }
}
