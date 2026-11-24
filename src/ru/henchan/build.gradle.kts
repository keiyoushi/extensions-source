import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HenChan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "multichan"

    source {
        baseUrl {
            custom("https://xxl.hentaichan.live")
        }
        lang = "ru"
        id = 5504588601186153612L
    }

    deeplink {
        host("h-chan.me")
        host("*.h-chan.me")
        host("hentaichan.live")
        host("*.hentaichan.live")
        path("/manga/..*\\.html")
    }
}
