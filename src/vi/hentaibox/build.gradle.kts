import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiBox"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://hentaibox.top"
    }

    deeplink {
        path("/truyen/..*")
        path("/the-loai/..*")
        path("/danh-sach")
        path("/tim-kiem")
    }
}
