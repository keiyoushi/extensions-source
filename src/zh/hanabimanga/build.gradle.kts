import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HanabiManga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "花火漫画"
        lang = "zh"
        baseUrl = "https://uhkvqrxmcapgtpspglrp.moedot.net"
    }
}
