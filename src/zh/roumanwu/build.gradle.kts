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
        // 使用自定义域名：用户可在扩展设置中填写任意可用域名（如原镜像 rouman5.com / roum29.xyz）
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
