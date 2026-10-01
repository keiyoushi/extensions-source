import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shenshi Huisuo"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "绅士会所"
        lang = "zh"
        baseUrl = "https://www.hentaiclub.net"
    }
}
