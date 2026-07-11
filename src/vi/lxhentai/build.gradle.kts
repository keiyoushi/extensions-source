import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        id = 6495630445796108150L
        lang = "vi"
        baseUrl {
            custom("https://lxmanga.space")
        }
    }

    deeplink {
        host("lxmanga.space")
        path("/truyen/..*")
    }
}
