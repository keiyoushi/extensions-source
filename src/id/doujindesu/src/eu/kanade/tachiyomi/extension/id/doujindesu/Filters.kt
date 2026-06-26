package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.Filter

class Category(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class Genre(name: String, val id: String = name) : Filter.CheckBox(name) {
    override fun toString(): String = id
}

class Order(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class Status(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class AuthorGroupSeriesOption(val display: String, val key: String) {
    override fun toString(): String = display
}

class AuthorGroupSeriesFilter(options: Array<AuthorGroupSeriesOption>) : Filter.Select<AuthorGroupSeriesOption>("Filter Tipe", options, 0)
class AuthorGroupSeriesValueFilter : Filter.Text("Nama")
class CharacterFilter : Filter.Text("Karakter")
class CategoryNames(categories: Array<Category>) : Filter.Select<Category>("Kategori", categories, 0)
class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Urutkan", orders, 0)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
class StatusList(statuses: Array<Status>) : Filter.Select<Status>("Status", statuses, 0)

val orderBy = arrayOf(
    Order("Semua", ""),
    Order("Baru Ditambahkan", "newest"),
    Order("Terlama", "oldest"),
    Order("Populer", "rating"),
    Order("A-Z", "title_asc"),

)

val statusList = arrayOf(
    Status("Semua", ""),
    Status("Berlanjut", "ongoing"),
    Status("Selesai", "completed"),
)

val categoryNames = arrayOf(
    Category("Semua", ""),
    Category("Doujinshi", "doujinshi"),
    Category("Manga", "manga"),
    Category("Manhwa", "manhwa"),
)

val authorGroupSeriesOptions = arrayOf(
    AuthorGroupSeriesOption("Tidak Ada", ""),
    AuthorGroupSeriesOption("Penulis", "authors"),
    AuthorGroupSeriesOption("Grup", "groups"),
    AuthorGroupSeriesOption("Genre", "genres"),
    AuthorGroupSeriesOption("Seri", "series"),
    AuthorGroupSeriesOption("Karakter", "characters"),
)

fun getGenreList() = listOf(
    Genre("Age Progression"),
    Genre("Age Regression"),
    Genre("Ahegao"),
    Genre("All The Way Through"),
    Genre("Amputee"),
    Genre("Anal"),
    Genre("Anorexia"),
    Genre("Apron"),
    Genre("Artist CG"),
    Genre("Aunt"),
    Genre("Bald"),
    Genre("Bestiality"),
    Genre("Big Ass"),
    Genre("Big Breast"),
    Genre("Big Penis"),
    Genre("Bike Shorts"),
    Genre("Bikini"),
    Genre("Birth"),
    Genre("Bisexual"),
    Genre("Blackmail"),
    Genre("Blindfold"),
    Genre("Bloomers"),
    Genre("Blowjob"),
    Genre("Body Swap"),
    Genre("Bodysuit"),
    Genre("Bondage"),
    Genre("Business Suit"),
    Genre("Cheating"),
    Genre("Collar"),
    Genre("Condom"),
    Genre("Cousin"),
    Genre("Crossdressing"),
    Genre("Cunnilingus"),
    Genre("DILF"),
    Genre("Dark Skin"),
    Genre("Daughter"),
    Genre("Defloration"),
    Genre("Demon"),
    Genre("Demon Girl"),
    Genre("Dick Growth"),
    Genre("Double Penetration"),
    Genre("Drugs"),
    Genre("Drunk"),
    Genre("Elf"),
    Genre("Emotionless Sex"),
    Genre("Exhibitionism"),
    Genre("Eyepatch"),
    Genre("Females Only"),
    Genre("Femdom"),
    Genre("Filming"),
    Genre("Fingering"),
    Genre("Footjob"),
    Genre("Full Color"),
    Genre("Furry"),
    Genre("Futanari"),
    Genre("Garter Belt"),
    Genre("Gender Bender"),
    Genre("Ghost"),
    Genre("Glasses"),
    Genre("Group"),
    Genre("Guro"),
    Genre("Gyaru"),
    Genre("Hairy"),
    Genre("Handjob"),
    Genre("Harem"),
    Genre("Horns"),
    Genre("Huge Breast"),
    Genre("Huge Penis"),
    Genre("Humiliation"),
    Genre("Impregnation"),
    Genre("Incest"),
    Genre("Inflation"),
    Genre("Insect"),
    Genre("Inseki"),
    Genre("Inverted Nipples"),
    Genre("Invisible"),
    Genre("Kemomi"),
    Genre("Kemomimi"),
    Genre("Kimono"),
    Genre("Lactation"),
    Genre("Leotard"),
    Genre("Lingerie"),
    Genre("Loli"),
    Genre("Lolipai"),
    Genre("MILF"),
    Genre("Maid"),
    Genre("Males Only"),
    Genre("Masturbation"),
    Genre("Miko"),
    Genre("Mind Break"),
    Genre("Mind Control"),
    Genre("Minigirl"),
    Genre("Miniguy"),
    Genre("Monster"),
    Genre("Monster Girl"),
    Genre("Mother"),
    Genre("Multi-work Series"),
    Genre("Muscle"),
    Genre("Nakadashi"),
    Genre("Necrophilia"),
    Genre("Netorare"),
    Genre("Niece"),
    Genre("Nipple Fuck"),
    Genre("Nurse"),
    Genre("Old Man"),
    Genre("Oyakodon"),
    Genre("Paizuri"),
    Genre("Pantyhose"),
    Genre("Possession"),
    Genre("Pregnant"),
    Genre("Prostitution"),
    Genre("Rape"),
    Genre("Rimjob"),
    Genre("Scat"),
    Genre("School Uniform"),
    Genre("Sex Toys"),
    Genre("Shemale"),
    Genre("Shota"),
    Genre("Sister"),
    Genre("Sleeping"),
    Genre("Slime"),
    Genre("Small Breast"),
    Genre("Snuff"),
    Genre("Sole Female"),
    Genre("Sole Male"),
    Genre("Stocking"),
    Genre("Story Arc"),
    Genre("Sumata"),
    Genre("Sweating"),
    Genre("Swimsuit"),
    Genre("Tanlines"),
    Genre("Teacher"),
    Genre("Tentacles"),
    Genre("Tomboy"),
    Genre("Tomgirl"),
    Genre("Torture"),
    Genre("Twins"),
    Genre("Twintails"),
    Genre("Uncensored"),
    Genre("Unusual Pupils"),
    Genre("Virginity"),
    Genre("Webtoon"),
    Genre("Widow"),
    Genre("X-Ray"),
    Genre("Yandere"),
    Genre("Yaoi"),
    Genre("Yuri"),
)
