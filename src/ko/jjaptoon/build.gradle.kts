import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jjaptoon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "짭툰"
        lang = "ko"
        baseUrl {
            custom("https://www.jjaptoon008.com")
        }
    }

    deeplink {
        host("www.jjaptoon008.com")
        host("jjaptoon008.com")
        path("/comics/..*")
        path("/chapters/..*")
    }
}
