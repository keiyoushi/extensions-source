import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiChan"
    versionCode = 0
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "multichan"

    source {
        baseUrl {
            custom("https://yaoi-chan.me")
        }
        lang = "ru"
        id = 2466512768990363955L
    }
}
