import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManHuaGui"
    versionCode = 28
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "漫画柜"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.manhuagui.com",
                "https://tw.manhuagui.com",
                "https://www.mhgui.com",
                "https://tw.mhgui.com",
            )
        }
    }

    deeplink {
        host("manhuagui.com")
        host("m.manhuagui.com")
        host("www.manhuagui.com")
        host("tw.manhuagui.com")
        host("mhgui.com")
        host("m.mhgui.com")
        host("www.mhgui.com")
        host("tw.mhgui.com")
        path("/comic/..*")
    }
}

dependencies {

    implementation(project(":lib:lzstring"))
    implementation(project(":lib:unpacker"))
}
