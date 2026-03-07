package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

/**
 * 禁漫天堂 API 常量配置
 * 基于移动端 API 协议
 */
object JmConstants {

    // ==================== API 密钥 ====================

    /** 标准接口 Token 签名密钥 */
    const val APP_TOKEN_SECRET = "18comicAPP"

    /** 特殊接口 Token 签名密钥（如 chapter_view_template） */
    const val APP_TOKEN_SECRET_2 = "18comicAPPContent"

    /** 响应数据 AES 解密密钥 */
    const val APP_DATA_SECRET = "185Hcomic3PAPP7R"

    /** 域名服务器密钥 */
    const val API_DOMAIN_SERVER_SECRET = "diosfjckwpqpdfjkvnqQjsik"

    /** APP 版本号 */
    const val APP_VERSION = "2.0.18"

    // ==================== API 域名 ====================

    /** 移动端 API 域名列表（会动态更新） */
    val API_DOMAIN_LIST = arrayOf(
        "www.cdnaspa.vip",
        "www.cdnaspa.club",
        "www.cdnplaystation6.vip",
        "www.cdnplaystation6.cc",
    )

    /** 获取最新 API 域名的服务器地址 */
    val API_DOMAIN_SERVER_LIST = arrayOf(
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
    )

    // ==================== API 端点 ====================

    /** 登录接口 */
    const val ENDPOINT_LOGIN = "/login"

    /** 初始化设置接口 */
    const val ENDPOINT_SETTING = "/setting"

    /** 搜索接口 */
    const val ENDPOINT_SEARCH = "/search"

    /** 分类筛选接口 */
    const val ENDPOINT_CATEGORIES_FILTER = "/categories/filter"

    /** 漫画详情接口 */
    const val ENDPOINT_ALBUM = "/album"

    /** 章节详情接口 */
    const val ENDPOINT_CHAPTER = "/chapter"

    /** 收藏列表接口 */
    const val ENDPOINT_FAVORITE = "/favorite"

    /** 章节查看模板接口（获取 scramble_id 等信息） */
    const val ENDPOINT_CHAPTER_VIEW_TEMPLATE = "/chapter_view_template"

    // ==================== 图片混淆常量 ====================

    /** 图片开始混淆的漫画 ID */
    const val SCRAMBLE_ID = 220980

    /** 第二个混淆算法阈值 */
    const val SCRAMBLE_268850 = 268850

    /** 第三个混淆算法阈值 */
    const val SCRAMBLE_421926 = 421926

    // ==================== 请求头常量 ====================

    /** 标准 User-Agent */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9; V1938CT Build/PQ3A.190705.11211812; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36"

    /** 图片请求专用 User-Agent */
    const val IMAGE_USER_AGENT = USER_AGENT

    /** 图片请求 X-Requested-With 头 */
    const val IMAGE_X_REQUESTED_WITH = "com.JMComic3.app"

    // ==================== SharedPreferences 键名 ====================

    /** 会话 Cookie（匿名/登录均可能使用） */
    const val PREF_LOGIN_COOKIE = "login_cookie"

    /** API 域名索引 */
    const val PREF_API_DOMAIN_INDEX = "api_domain_index"

    /** API 域名列表（动态更新） */
    const val PREF_API_DOMAIN_LIST = "api_domain_list"

    /** API 域名显示标签列表（与域名索引对齐，JSON 数组） */
    const val PREF_API_DOMAIN_LABEL_LIST = "api_domain_label_list"
}
