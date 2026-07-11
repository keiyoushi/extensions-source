import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shenshi Huisuo"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "绅士会所"
        lang = "zh"
        baseUrl = "https://www.hentaiclub.net"
    }
}
