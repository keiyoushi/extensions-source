import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baozi Manhua"
    versionCode = 29
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "包子漫画"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://cn.baozimh.com",
                "https://tw.baozimh.com",
                "https://www.baozimh.com",
                "https://cn.webmota.com",
                "https://tw.webmota.com",
                "https://www.webmota.com",
                "https://cn.kukuc.co",
                "https://tw.kukuc.co",
                "https://www.kukuc.co",
                "https://cn.twmanga.com",
                "https://tw.twmanga.com",
                "https://www.twmanga.com",
                "https://cn.dinnerku.com",
                "https://tw.dinnerku.com",
                "https://www.dinnerku.com",
                "https://appcn.baozimh.com",
                "https://appgb.baozimh.com",
            )
        }
        id = 5724751873601868259L
    }

    deeplink {
        host("baozimh.com")
        host("*.baozimh.com")
        host("webmota.com")
        host("*.webmota.com")
        host("kukuc.co")
        host("*.kukuc.co")
        host("twmanga.com")
        host("*.twmanga.com")
        host("dinnerku.com")
        host("*.dinnerku.com")
        path("/comic/..*")
        path("/comic/chapter/..*")
    }
}
