import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenViet (unoriginal)"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://nettruyenviet10.com")
        }
    }

    deeplink {
        host("nettruyenviet10.com")
        host("www.nettruyenviet10.com")
        host("nettruyenviet11.com")
        host("www.nettruyenviet11.com")
        host("hinettruyen.com")
        host("www.hinettruyen.com")
        path("/truyen-tranh/..*")
    }
}
