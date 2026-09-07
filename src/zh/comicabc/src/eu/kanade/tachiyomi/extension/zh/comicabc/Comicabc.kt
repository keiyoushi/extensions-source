package eu.kanade.tachiyomi.extension.zh.comicabc

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Comicabc : HttpSource() {
    override val supportsLatest: Boolean = true
    private val chaptersBaseUrl: String = "https://articles.onemoreplace.tw"

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comic/h-$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row a.comicpic_col6").map { element ->
            SManga.create().apply {
                title = element.selectFirst("li.nowraphide")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.pager a span.mdi-skip-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comic/u-$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row .cat2_list a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("li.nowraphide")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.pager a span.mdi-skip-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    // The site's own simplified->traditional fallback misses some chars, so search again
    // client-side when a keyword returns nothing (e.g. "复仇者学院").
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/member/search.aspx".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row a.comicpic_col6").map { element ->
            SManga.create().apply {
                title = element.selectFirst("li.nowraphide")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        if (mangas.isEmpty() && response.request.url.queryParameter("page") == "1") {
            val query = response.request.url.queryParameter("key").orEmpty()
            val converted = query.toTraditional()
            if (converted != query) {
                val url = "$baseUrl/member/search.aspx".toHttpUrl().newBuilder()
                    .addQueryParameter("key", converted)
                    .addQueryParameter("page", "1")
                    .build()
                val retryResponse = client.newCall(GET(url, headers)).execute()
                val retryDocument = retryResponse.asJsoup()
                val retryMangas = retryDocument.select(".container .row a.comicpic_col6").map { element ->
                    SManga.create().apply {
                        title = element.selectFirst("li.nowraphide")!!.text()
                        setUrlWithoutDomain(element.absUrl("href"))
                        thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    }
                }
                val hasNextPage = retryDocument.selectFirst("div.pager a span.mdi-skip-next") != null
                return MangasPage(retryMangas, hasNextPage)
            }
        }
        val hasNextPage = document.selectFirst("div.pager a span.mdi-skip-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst(".item_content_box .h2")!!.text()
        thumbnail_url = document.selectFirst(".item-cover img")?.absUrl("src")
        author = document.selectFirst(".item_content_box .item-info-author")?.text()?.substringAfter("作者: ")
        artist = author
        description = document.selectFirst(".item_content_box .item_info_detail")?.text()
        status = when (document.selectFirst(".item_content_box .item-info-status")?.text()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapters a, .comic_chapters a").map { element ->
            SChapter.create().apply {
                name = element.text()
                val onclick = element.attr("onclick")

                if (onclick.contains("cview")) {
                    val params = onclick.substringAfter("cview('").substringBefore("'")
                    val comicId = params.substringBefore("-")
                    val chapterIdWithHtml = params.substringAfter("-")
                    val chapterId = chapterIdWithHtml.substringBefore(".html")
                    url = "$chaptersBaseUrl/online/new-$comicId.html?ch=$chapterId"
                } else {
                    val href = element.attr("href")
                    url = when {
                        href.startsWith("/online/") -> "$chaptersBaseUrl$href"
                        href.startsWith("http") -> href
                        else -> element.absUrl("href")
                    }
                }
            }
        }.reversed()
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val pageListHeaders = headersBuilder().add("Referer", "$baseUrl/").build()
        return GET(chapter.url, pageListHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageUrl = response.request.url.toString()
        val html = response.body.string()

        val targetScriptContent = scriptRegex.findAll(html)
            .map { it.groupValues[1] }
            .find { it.contains("""$("#comics-pics").html(xx)""") }
            ?: throw Exception("无法找到包含图片数据的脚本")

        val scriptContent = targetScriptContent
            .replace("document.location", "'$pageUrl'")
            .substringBefore("""$("#comics-pics")""")

        val urlCreationLogic = urlCreationLogicRegex.find(scriptContent)
            ?.groupValues
            ?.get(1) ?: throw Exception("无法捕获URL生成逻辑")

        val scriptToExecute =
            """
            $J_JS_FUNCTIONS
            $scriptContent

            var urls = [];
            for (var j = 1; j <= ps; j++) {
                var s = 'https:' + unescape($urlCreationLogic);
                urls.push(s);
            }
            urls;
            """.trimIndent()

        val quickJs = QuickJs.create()
        quickJs.use { quickJs ->
            val result = quickJs.evaluate(scriptToExecute)
            if (result is Array<*>) {
                return result.mapIndexed { index, url -> Page(index, imageUrl = url.toString()) }
            }
        }
        return emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder().add("Referer", "$chaptersBaseUrl/").build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val scriptRegex = Regex("""<script language="javascript">([\s\S]*?)</script>""")
        private val urlCreationLogicRegex = Regex("""s="'\s*\+\s*(.*?)\s*\+\s*'"\s*draggable""")

        // Simplified->traditional char map (PowerShell StrConv(VbStrConv.TraditionalChinese, zh-CN)),
        // compact "简繁" pair format, used for the search retry above.
        private const val S2T_PAIRS =
            "万萬与與专專业業丛叢东東丝絲丢丟两兩严嚴丧喪个個丰豐临臨为為丽麗举舉义義乌烏乐樂乔喬习習乡鄉书書买買乱亂争爭亏虧亘亙亚亞产產亩畝亲親亵褻亿億仅僅从從仑侖仓倉仪儀们們价價众眾优優会會伛傴伞傘伟偉传傳伤傷伥倀伦倫伧傖伪偽伫佇体體佣傭佥僉侠俠侣侶侥僥侦偵侧側侨僑侩儈侪儕侬儂俣俁俦儔俨儼俩倆俪儷俭儉债債倾傾偬傯偻僂偾僨偿償傥儻傧儐储儲傩儺儿兒兑兌兖兗党黨兰蘭关關兴興兹茲养養兽獸冁囅内內冈岡册冊写寫军軍农農冯馮冲沖决決况況冻凍净凈准準凉涼减減凑湊凛凜几幾凤鳳凫鳧凭憑凯凱凶兇击擊凿鑿刍芻划劃刘劉则則刚剛创創删刪别別刭剄刹剎刽劊刿劌剀剴剂劑剐剮剑劍剥剝剧劇劝勸办辦务務劢勱动動励勵劲勁劳勞势勢勋勛匀勻匦匭匮匱区區医醫华華协協单單卖賣卢盧卤鹵卧臥卫衛却卻卺巹厂廠厅廳历歷厉厲压壓厌厭厍厙厕廁厢廂厣厴厦廈厨廚厩廄厮廝县縣参參双雙发發变變叙敘叠疊台臺叶葉号號叹嘆叽嘰吓嚇吕呂吗嗎吨噸听聽启啟吴吳呐吶呒嘸呓囈呕嘔呖嚦呗唄员員呙咼呛嗆呜嗚咏詠咙嚨咛嚀响響哑啞哒噠哓嘵哔嗶哕噦哗嘩哙噲哜嚌哝噥哟喲唛嘜唠嘮唢嗩唤喚啧嘖啬嗇啭囀啮嚙啸嘯喷噴喽嘍喾嚳嗫囁嗳噯嘘噓嘤嚶嘱囑噜嚕嚣囂团團园園囱囪围圍囵圇国國图圖圆圓圹壙场場坏壞块塊坚堅坛壇坜壢坝壩坞塢坟墳坠墜垄壟垆壚垒壘垦墾垧坰垩堊垫墊垭埡垲塏埘塒埙塤埚堝堑塹堕墮墒墑墙墻壮壯声聲壳殼壶壺处處备備复復够夠头頭夹夾夺奪奁奩奂奐奋奮奖獎奥奧妆妝妇婦妈媽妩嫵妪嫗妫媯姗姍娄婁娅婭娆嬈娇嬌娈孌娱娛娲媧娴嫻婴嬰婵嬋婶嬸媪媼嫒嬡嫔嬪嫱嬙嬷嬤孙孫学學孪孿宁寧宝寶实實宠寵审審宪憲宫宮宽寬宾賓寝寢对對寻尋导導寿壽将將尔爾尘塵尝嘗尧堯尴尷尽盡层層屉屜届屆属屬屡屢屦屨屿嶼岁歲岂豈岖嶇岗崗岘峴岚嵐岛島岩巖岭嶺岽崠岿巋峄嶧峡峽峤嶠峥崢峦巒崂嶗崃崍崭嶄嵘嶸嵛崳嵝嶁巅巔巩鞏巯巰币幣帅帥师師帏幃帐帳帘簾帜幟带帶帧幀帮幫帱幬帻幘帼幗幂冪广廣庄莊庆慶庐廬庑廡库庫应應庙廟庞龐废廢廪廩开開异異弃棄弑弒张張弥彌弪弳弯彎弹彈强強归歸当當录錄彦彥彻徹径徑徕徠忆憶忏懺忧憂忾愾怀懷态態怂慫怃憮怄慪怅悵怆愴怜憐总總怼懟怿懌恋戀恳懇恶惡恸慟恹懨恺愷恻惻恼惱恽惲悦悅悫愨悬懸悭慳悯憫惊驚惧懼惨慘惩懲惫憊惬愜惭慚惮憚惯慣愠慍愤憤愦憒慑懾懑懣懒懶戆戇戋戔戏戲戗戧战戰戬戩户戶扑撲扞捍执執扩擴扪捫扫掃扬揚扰擾抚撫抛拋抟摶抠摳抡掄抢搶护護报報担擔拟擬拢攏拣揀拥擁拦攔拧擰拨撥择擇挂掛挚摯挛攣挝撾挞撻挟挾挠撓挡擋挢撟挣掙挤擠挥揮捞撈损損捡撿换換捣搗据據掳擄掴摑掷擲掸撣掺摻掼摜揽攬揿撳搀攙搁擱搂摟搅攪携攜摄攝摅攄摆擺摇搖摈擯摊攤撄攖撑撐撵攆撷擷撸擼撺攛擀搟擞擻攒攢敌敵敛斂数數斋齋斓斕斩斬断斷无無旧舊时時旷曠昙曇昼晝显顯晋晉晒曬晓曉晔曄晕暈晖暉暂暫暧曖术術朴樸机機杀殺杂雜权權杆桿条條来來杨楊杩榪极極构構枞樅枢樞枣棗枥櫪枨棖枪槍枫楓枭梟柠檸柽檉栀梔栅柵标標栈棧栉櫛栊櫳栋棟栌櫨栎櫟栏欄树樹栖棲样樣栾欒桠椏桡橈桢楨档檔桤榿桥橋桦樺桧檜桨槳桩樁梦夢检檢棂欞椁槨椟櫝椠槧椤欏椭橢楼樓榄欖榇櫬榈櫚榉櫸槛檻槟檳槠櫧横橫樯檣樱櫻橱櫥橹櫓橼櫞檩檁欢歡欤歟欧歐歼殲殁歿殇殤残殘殒殞殓殮殚殫殡殯殴毆毁毀毂轂毕畢毙斃毡氈毵毿氇氌气氣氢氫氩氬氲氳汇匯汉漢汤湯汹洶沟溝没沒沣灃沤漚沥瀝沦淪沧滄沩溈沪滬泞濘泪淚泶澩泷瀧泸瀘泺濼泻瀉泼潑泽澤泾涇洁潔洒灑浃浹浅淺浆漿浇澆浈湞浊濁测測浍澮济濟浏瀏浑渾浒滸浓濃浔潯涛濤涝澇涞淶涟漣涠潿涡渦涣渙涤滌润潤涧澗涨漲涩澀渊淵渌淥渍漬渎瀆渐漸渑澠渔漁渖瀋渗滲温溫湾灣湿濕溃潰溅濺滗潷滚滾滞滯滠灄满滿滢瀅滤濾滥濫滦灤滨濱滩灘潆瀠潇瀟潋瀲潍濰潜潛澜瀾濑瀨濒瀕灏灝灭滅灯燈灵靈灾災灿燦炀煬炉爐炖燉炜煒炝熗点點炼煉炽熾烁爍烂爛烃烴烛燭烟煙烦煩烧燒烨燁烩燴烫燙烬燼热熱焕煥焖燜焘燾爱愛爷爺牍牘牵牽牺犧犊犢状狀犷獷犹猶狈狽狞獰独獨狭狹狮獅狯獪狰猙狱獄狲猻狸貍猃獫猎獵猕獼猡玀猪豬猫貓献獻獭獺玑璣玛瑪玮瑋环環现現玺璽珏玨珐琺珑瓏珲琿琅瑯琏璉琐瑣琼瓊瑶瑤瑷璦璎瓔瓒瓚瓮甕瓯甌电電画畫畅暢畲畬畴疇疖癤疗療疟瘧疠癘疡瘍疮瘡疯瘋疱皰症癥痈癰痉痙痒癢痨癆痪瘓痫癇痴癡瘅癉瘗瘞瘘瘺瘪癟瘫癱瘾癮瘿癭癞癩癣癬癫癲皑皚皱皺皲皸盏盞盐鹽监監盖蓋盗盜盘盤眦眥眯瞇着著睁睜睃脧睐睞睑瞼睾睪瞒瞞瞩矚矫矯矶磯矾礬矿礦砀碭码碼砖磚砗硨砚硯砺礪砻礱砾礫础礎硕碩硖硤硗磽确確硷鹼碍礙碛磧碜磣碱堿礼禮祢禰祯禎祷禱祸禍禀稟禄祿禅禪离離秃禿秆稈种種积積称稱秽穢税稅稣穌稳穩穑穡穷窮窃竊窍竅窑窯窜竄窝窩窥窺窦竇窭窶竖豎竞競笃篤笋筍笔筆笕筧笺箋笼籠笾籩筚篳筛篩筝箏筹籌签簽简簡箦簀箧篋箨籜箩籮箪簞箫簫篑簣篓簍篮籃篱籬簖籪籁籟籴糴类類籼秈粜糶粝糲粤粵粪糞粮糧糁糝紧緊絷縶纠糾纡紆红紅纣紂纤纖纥紇约約级級纨紈纩纊纪紀纫紉纬緯纭紜纯純纰紕纱紗纲綱纳納纵縱纶綸纷紛纸紙纹紋纺紡纽紐纾紓线線绀紺绁紲绂紱练練组組绅紳细細织織终終绉縐绊絆绋紼绌絀绍紹绎繹经經绐紿绑綁绒絨结結绕繞绗絎绘繪给給绚絢绛絳络絡绝絕绞絞统統绠綆绡綃绢絹绣繡绥綏绦絳继繼绨綈绩績绪緒绫綾续續绮綺绯緋绰綽绲緄绳繩维維绵綿绶綬绷繃绸綢绺綹绻綣综綜绽綻绾綰绿綠缀綴缁緇缂緙缃緗缄緘缅緬缆纜缇緹缈緲缉緝缋繢缌緦缍綞缎緞缏緶缑緱缒縋缓緩缔締缕縷编編缗緡缘緣缙縉缚縛缛縟缜縝缝縫缟縞缠纏缡縭缢縊缣縑缤繽缥縹缦縵缧縲缨纓缩縮缪繆缫繅缬纈缭繚缮繕缯繒缰韁缱繾缲繰缳繯缴繳缵纘罂罌网網罗羅罚罰罢罷罴羆羁羈羟羥羡羨翘翹耧耬耸聳耻恥聂聶聋聾职職聍聹联聯聩聵聪聰肃肅肠腸肤膚肮骯肾腎肿腫胀脹胁脅胆膽胜勝胧朧胪臚胫脛胶膠脉脈脍膾脏臟脐臍脑腦脓膿脔臠脚腳脱脫脶腡脸臉腊臘腻膩腼靦腽膃腾騰膑臏舆輿舣艤舰艦舱艙舻艫艰艱艳艷艹艸艺藝节節芈羋芗薌芜蕪芦蘆芸蕓苁蓯苄芐苇葦苈藶苋莧苌萇苍蒼苎苧苏蘇苟茍苹蘋茎莖茏蘢茑蔦茔塋茕煢茧繭荆荊荐薦荚莢荛蕘荜蓽荞蕎荟薈荠薺荡蕩荣榮荤葷荥滎荦犖荧熒荨蕁荩藎荪蓀荫蔭荭葒药藥莅蒞莱萊莲蓮莳蒔莴萵莶薟获獲莸蕕莹瑩莺鶯萝蘿萤螢营營萦縈萧蕭萨薩葱蔥蒇蕆蒉蕢蒋蔣蒌蔞蓝藍蓟薊蓠蘺蓣蕷蓥鎣蓦驀蔷薔蔹蘞蔺藺蔼藹蕲蘄蕴蘊薮藪藓蘚蘖蘗虏虜虑慮虚虛虫蟲虮蟣虽雖虾蝦虿蠆蚀蝕蚁蟻蚂螞蚕蠶蚝蠔蚬蜆蛊蠱蛎蠣蛏蟶蛮蠻蛰蟄蛱蛺蛲蟯蛳螄蛴蠐蜕蛻蜗蝸蜡蠟蝇蠅蝈蟈蝉蟬蝼螻蝾蠑衅釁衔銜补補衬襯衮袞袄襖袅裊袜襪袭襲装裝裆襠裢褳裣襝裤褲褛褸褴襤见見观觀规規觅覓视視觇覘览覽觉覺觊覬觋覡觌覿觎覦觏覯觐覲觑覷觞觴触觸觯觶誉譽誊謄计計订訂讣訃认認讥譏讦訐讧訌讨討让讓讪訕讫訖训訓议議讯訊记記讲講讳諱讴謳讵詎讶訝讷訥许許讹訛论論讼訟讽諷设設访訪诀訣证證诂詁诃訶评評诅詛识識诈詐诉訴诊診诋詆诌謅词詞诎詘诏詔译譯诒詒诓誆诔誄试試诖詿诗詩诘詰诙詼诚誠诛誅诜詵话話诞誕诟詬诠詮诡詭询詢诣詣诤諍该該详詳诧詫诨諢诩詡诫誡诬誣语語诮誚误誤诰誥诱誘诲誨诳誑说說诵誦诶誒请請诸諸诹諏诺諾读讀诼諑诽誹课課诿諉谀諛谁誰谂諗调調谄諂谅諒谆諄谇誶谈談谊誼谋謀谌諶谍諜谎謊谏諫谐諧谑謔谒謁谓謂谔諤谕諭谖諼谗讒谘諮谙諳谚諺谛諦谜謎谝諞谟謨谠讜谡謖谢謝谣謠谤謗谥謚谦謙谧謐谨謹谩謾谪謫谬謬谭譚谮譖谯譙谰讕谱譜谲譎谳讞谴譴谵譫谶讖贝貝贞貞负負贡貢财財责責贤賢败敗账賬货貨质質贩販贪貪贫貧贬貶购購贮貯贯貫贰貳贱賤贲賁贳貰贴貼贵貴贶貺贷貸贸貿费費贺賀贻貽贼賊贽贄贾賈贿賄赀貲赁賃赂賂赃贓资資赅賅赆贐赇賕赈賑赉賚赊賒赋賦赌賭赎贖赏賞赐賜赓賡赔賠赕賧赖賴赘贅赙賻赚賺赛賽赜賾赝贗赞贊赠贈赡贍赢贏赣贛赵趙赶趕趋趨趱趲趸躉跃躍跄蹌跞躒践踐跷蹺跸蹕跹躚跻躋踊踴踌躊踪蹤踬躓踯躑蹑躡蹒蹣蹰躕蹿躥躏躪躜躦躯軀车車轧軋轨軌轩軒轫軔转轉轭軛轮輪软軟轰轟轲軻轳轤轴軸轵軹轶軼轸軫轹轢轺軺轻輕轼軾载載轾輊轿轎辁輇辂輅较較辄輒辅輔辆輛辇輦辈輩辉輝辊輥辋輞辍輟辎輜辏輳辐輻辑輯输輸辔轡辕轅辖轄辗輾辘轆辙轍辚轔辞辭辩辯辫辮边邊辽遼达達迁遷过過迈邁运運还還这這进進远遠违違连連迟遲迩邇迳逕迹跡适適选選逊遜递遞逦邐逻邏遗遺遥遙邓鄧邝鄺邬鄔邮郵邹鄒邺鄴邻鄰郏郟郐鄶郑鄭郓鄆郦酈郧鄖郸鄲酝醞酱醬酽釅酾釃酿釀释釋鉴鑒銮鑾錾鏨钆釓钇釔针針钉釘钊釗钋釙钌釕钍釷钎釬钏釧钐釤钒釩钓釣钔鍆钕釹钗釵钙鈣钛鈦钜鉅钝鈍钞鈔钟鐘钠鈉钡鋇钢鋼钣鈑钤鈐钥鑰钦欽钧鈞钨鎢钩鉤钪鈧钫鈁钬鈥钭鈄钮鈕钯鈀钰鈺钱錢钲鉦钳鉗钴鈷钵缽钶鈳钸鈽钹鈸钺鉞钻鉆钼鉬钽鉭钾鉀钿鈿铀鈾铁鐵铂鉑铃鈴铄鑠铅鉛铆鉚铈鈰铉鉉铊鉈铋鉍铌鈮铍鈹铎鐸铐銬铑銠铒鉺铕銪铖鋮铗鋏铙鐃铛鐺铜銅铝鋁铟銦铠鎧铡鍘铢銖铣銑铤鋌铥銩铧鏵铨銓铩鎩铪鉿铫銚铬鉻铭銘铮錚铯銫铰鉸铱銥铲鏟铳銃铴鐋铵銨银銀铷銣铸鑄铹鐒铺鋪铼錸铽鋱链鏈铿鏗销銷锁鎖锂鋰锄鋤锅鍋锆鋯锇鋨锈銹锉銼锊鋝锋鋒锌鋅锐銳锑銻锒鋃锓鋟锔鋦锕錒锖錆锗鍺错錯锚錨锛錛锞錁锟錕锡錫锢錮锣鑼锤錘锥錐锦錦锩錈锬錟锭錠键鍵锯鋸锰錳锱錙锲鍥锴鍇锵鏘锶鍶锷鍔锸鍤锹鍬锺鍾锻鍛锼鎪锾鍰镀鍍镁鎂镂鏤镄鐨镆鏌镇鎮镉鎘镊鑷镌鐫镍鎳镏鎦镐鎬镑鎊镒鎰镓鎵镔鑌镖鏢镗鏜镘鏝镙鏍镛鏞镜鏡镝鏑镞鏃镟鏇镡鐔镣鐐镤鏷镦鐓镧鑭镨鐠镪鏹镫鐙镬鑊镭鐳镯鐲镰鐮镱鐿镳鑣镶鑲长長门門闩閂闪閃闫閆闭閉问問闯闖闰閏闱闈闲閑闳閎间間闵閔闶閌闷悶闸閘闹鬧闺閨闻聞闼闥闽閩闾閭阀閥阁閣阂閡阃閫阄鬮阅閱阆閬阈閾阉閹阊閶阋鬩阌閿阍閽阎閻阏閼阐闡阑闌阒闃阔闊阕闋阖闔阗闐阙闕阚闞队隊阳陽阴陰阵陣阶階际際陆陸陇隴陈陳陉陘陕陜陧隉陨隕险險随隨隐隱隶隸隽雋难難雏雛雠讎雳靂雾霧霁霽霭靄靓靚静靜靥靨鞑韃鞯韉韦韋韧韌韩韓韪韙韫韞韬韜韵韻页頁顶頂顷頃顸頇项項顺順须須顼頊顽頑顾顧顿頓颀頎颁頒颂頌颃頏预預颅顱领領颇頗颈頸颉頡颊頰颌頜颍潁颏頦颐頤频頻颓頹颔頷颖穎颗顆题題颚顎颛顓颜顏额額颞顳颟顢颠顛颡顙颢顥颤顫颦顰颧顴风風飑颮飒颯飓颶飕颼飘飄飙飆飞飛飨饗餍饜饥饑饧餳饨飩饩餼饪飪饫飫饬飭饭飯饮飲饯餞饰飾饱飽饲飼饴飴饵餌饶饒饷餉饺餃饼餅饽餑饿餓馁餒馄餛馅餡馆館馈饋馊餿馋饞馍饃馏餾馐饈馑饉馒饅馔饌马馬驭馭驮馱驯馴驰馳驱驅驳駁驴驢驵駔驶駛驷駟驸駙驹駒驺騶驻駐驼駝驽駑驾駕驿驛骀駘骁驍骂罵骄驕骅驊骆駱骇駭骈駢骊驪骋騁验驗骏駿骐騏骑騎骒騍骓騅骖驂骗騙骘騭骚騷骛騖骜驁骝騮骞騫骟騸骠驃骡騾骢驄骣驏骤驟骥驥骧驤髅髏髋髖髌髕鬓鬢魇魘魉魎鱼魚鱿魷鲁魯鲂魴鲈鱸鲋鮒鲍鮑鲎鱟鲐鮐鲑鮭鲒鮚鲔鮪鲕鮞鲚鱭鲛鮫鲜鮮鲟鱘鲠鯁鲡鱺鲢鰱鲣鰹鲤鯉鲥鰣鲦鰷鲧鯀鲨鯊鲩鯇鲫鯽鲭鯖鲮鯪鲰鯫鲱鯡鲲鯤鲳鯧鲵鯢鲶鯰鲷鯛鲸鯨鲻鯔鲽鰈鳃鰓鳄鱷鳅鰍鳆鰒鳇鰉鳌鰲鳍鰭鳎鰨鳏鰥鳐鰩鳓鰳鳔鰾鳕鱈鳖鱉鳗鰻鳜鱖鳝鱔鳞鱗鳟鱒鳢鱧鸟鳥鸠鳩鸡雞鸢鳶鸣鳴鸥鷗鸦鴉鸨鴇鸩鴆鸪鴣鸫鶇鸬鸕鸭鴨鸯鴦鸱鴟鸲鴝鸳鴛鸵鴕鸶鷥鸷鷙鸸鴯鸹鴰鸺鵂鸽鴿鸾鸞鸿鴻鹁鵓鹂鸝鹃鵑鹄鵠鹅鵝鹆鵒鹇鷴鹈鵜鹉鵡鹊鵲鹌鵪鹎鵯鹏鵬鹑鶉鹕鶘鹗鶚鹘鶻鹚鶿鹜鶩鹞鷂鹣鶼鹤鶴鹦鸚鹧鷓鹨鷚鹩鷯鹪鷦鹫鷲鹬鷸鹭鷺鹰鷹鹳鸛鹾鹺麦麥麸麩麽麼黄黃黉黌黩黷黪黲黾黽鼋黿鼍鼉鼹鼴齐齊齑齏齿齒龀齔龃齟龄齡龅齙龆齠龇齜龈齦龉齬龊齪龋齲龌齷龙龍龚龔龛龕龟龜"

        private val S2T = S2T_PAIRS.toList().chunked(2)
            .associate { it[0] to it[1] }

        private fun String.toTraditional(): String = buildString(length) {
            for (ch in this@toTraditional) append(S2T[ch] ?: ch)
        }

        // Core functions from j.js, required for the script to run
        private const val J_JS_FUNCTIONS =
            """
            function lc(l){if(l.length!=2)return l;var az="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";var a=l.substring(0,1);var b=l.substring(1,2);if(a=="Z")return 8000+az.indexOf(b);else return az.indexOf(a)*52+az.indexOf(b)}
            function su(a,b,c){var e=(a+'').substring(b,b+c);return e}
            function nn(n){return n<10?'00'+n:n<100?'0'+n:n}
            function mm(p){return(parseInt((p-1)/10)%10)+(((p-1)%10)*3)}
            """
    }
}
