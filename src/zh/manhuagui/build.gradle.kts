plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManHuaGui"
    versionCode = 28
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "漫画柜"
        lang = "zh"
        baseUrl("https://www.manhuagui.com") {
            mirrors = listOf(
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
