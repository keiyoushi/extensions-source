import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 22
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz4.xyz/dizhi
        // 默认内置域名；扩展设置中的「自定义基础 URL」留空即使用此默认域名，
        // 填写其它可用域名则可切换。
        baseUrl {
            custom("https://rouman5.com")
        }
    }

    deeplink {
        host("rouman5.com")
        host("roum29.xyz")
        path("/books/..*")
    }
}
