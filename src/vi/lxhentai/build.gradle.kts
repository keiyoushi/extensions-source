import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    versionCode = 34
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        id = 6495630445796108150L
        lang = "vi"
        baseUrl {
            custom("https://lxmanga.space")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
