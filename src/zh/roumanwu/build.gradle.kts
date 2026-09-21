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
        // 官方公布的内置域名（常用镜像站点）如下，二选一：
        //   - https://rouman5.com
        //   - https://roum29.xyz
        // custom(...) 仅用于保留 baseUrl 字段（使 DSL 不报错）；
        // 实际内置域名下拉与「使用自定义域名」开关由 Roumanwu.kt 自建，
        // 最终选中的域名会写入 overrideBaseUrl 供 baseUrl 读取。
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
