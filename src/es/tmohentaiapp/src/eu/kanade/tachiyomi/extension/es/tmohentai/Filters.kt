package eu.kanade.tachiyomi.extension.es.tmohentaiapp

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildTmohFilterList() = FilterList(
    Filter.Header("Búsqueda"),
    TitleFilter(),
    ArtistFilter(),
    GroupFilter(),
    Filter.Separator(),
    Filter.Header("Ordenar"),
    SortFilter(),
    Filter.Separator(),
    Filter.Header("Contenido"),
    ContentFilter(),
    Filter.Separator(),
    TagFilter(getTagList()),
)

class TitleFilter : Filter.Text("Nombre")
class ArtistFilter : Filter.Text("Artista")
class GroupFilter : Filter.Text("Quién lo subió")

class SortFilter :
    Filter.Select<String>(
        "Ordenar por",
        arrayOf("Popularidad (Likes)", "Más reciente", "Alfabético (A-Z)", "Puntuación", "Más visto"),
    ) {
    fun toUriPart(): Pair<String, String> = when (state) {
        0 -> Pair("likes_count", "desc")
        1 -> Pair("creation_date", "desc")
        2 -> Pair("alphabetically", "asc")
        3 -> Pair("score", "desc")
        4 -> Pair("views", "desc")
        else -> Pair("likes_count", "desc")
    }
}

class ContentFilter :
    Filter.Select<String>(
        "Contenido",
        arrayOf("Todo", "Yaoi", "Yuri", "Futanari", "Solo Femenino", "Solo Masculino", "Vanilla", "NTR / Netorare", "Uncensored"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "yaoi"
        2 -> "yuri"
        3 -> "futanari"
        4 -> "sole-female"
        5 -> "sole-male"
        6 -> "vanilla"
        7 -> "ntr"
        8 -> "uncensored"
        else -> ""
    }
}

class Tag(name: String, val value: String) : Filter.TriState(name)
class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Filtro de Tags", tags)

fun getTagList(): List<Tag> = listOf(
    Tag("NTR", "1"), Tag("Milf", "2"), Tag("Impregnation", "3"),
    Tag("Netorare", "4"), Tag("Big Breasts", "5"), Tag("Adultery", "6"),
    Tag("Ahegao", "7"), Tag("Blowjob", "8"), Tag("Cheating", "9"),
    Tag("Mature", "10"), Tag("Widow", "11"), Tag("Incest", "12"),
    Tag("Harem", "13"), Tag("Tentacles", "14"), Tag("Vanilla", "15"),
    Tag("Anal", "16"), Tag("Yuri", "17"), Tag("Yaoi", "18"),
    Tag("Futa", "19"), Tag("Mind Break", "20"), Tag("Rape", "21"),
    Tag("Schoolgirl", "22"), Tag("BBW", "23"), Tag("Sole Male", "24"),
    Tag("Sole Female", "25"), Tag("Group", "26"), Tag("Big Ass", "27"),
    Tag("Big Boobs", "28"), Tag("Dark Skin", "29"), Tag("Domination", "30"),
    Tag("Colour", "31"), Tag("Furry", "32"), Tag("Pregnant", "33"),
    Tag("Exhibitionism", "34"), Tag("Monsters", "35"), Tag("Humiliation", "36"),
    Tag("Shota", "37"), Tag("Creampie", "38"), Tag("Forced", "39"),
    Tag("Nympho", "40"), Tag("Toys", "41"), Tag("Deepthroat", "42"),
    Tag("Kissing", "43"), Tag("Futanari", "44"), Tag("Gyaru", "45"),
    Tag("FFM Threesome", "46"), Tag("MMF Threesome", "47"), Tag("Orgy", "48"),
    Tag("Double Penetration", "49"), Tag("Fantasy", "50"), Tag("Bestiality", "51"),
    Tag("Bukkake", "52"), Tag("Oyakodon", "53"), Tag("Mother", "54"),
    Tag("Tomboy", "55"), Tag("Tall Girl", "56"), Tag("Loli", "57"),
    Tag("Student", "58"), Tag("Femdom", "59"), Tag("Bondage", "60"),
    Tag("Fetish", "61"), Tag("Horror", "62"), Tag("Uncensored", "63"),
    Tag("Romance", "64"), Tag("Filming", "65"), Tag("Parody", "66"),
    Tag("Small Breasts", "67"), Tag("3D", "68"), Tag("Virgin", "69"),
    Tag("Yandere", "70"), Tag("Comedy", "71"), Tag("Bisexual", "72"),
    Tag("Netorase", "73"), Tag("Tsundere", "74"), Tag("Big Breast", "75"),
    Tag("Footjob", "76"), Tag("Sport", "77"), Tag("Domination Loss", "78"),
    Tag("Mesugaki", "79"), Tag("Teacher", "80"), Tag("Virginity", "81"),
    Tag("Angel", "82"), Tag("Blindfold", "83"), Tag("Catgirl", "84"),
    Tag("Digital", "85"), Tag("Foot Licking", "86"), Tag("Halo", "87"),
    Tag("Kemonomimi", "88"), Tag("Lingerie", "89"), Tag("Multi-Work Series", "90"),
    Tag("Nakadashi", "91"), Tag("Sleeping", "92"), Tag("Small Breasts", "93"),
    Tag("Stomach Deformation", "94"), Tag("Twintails", "95"),
    Tag("Very Long Hair", "96"), Tag("X-Ray", "97"), Tag("Mind Control", "98"),
    Tag("Defloration", "101"), Tag("Eyemask", "102"), Tag("Fishnets", "103"),
    Tag("Glasses", "111"), Tag("Large Tattoo", "112"), Tag("Muscle", "113"),
    Tag("Sex Toys", "114"), Tag("Stockings", "115"), Tag("Swimsuit", "117"),
    Tag("Breast Feeding", "118"), Tag("Condom", "119"), Tag("Hairy", "120"),
    Tag("Pantyhose", "122"), Tag("Beauty Mark", "123"), Tag("Dilf", "124"),
    Tag("Masturbation", "125"), Tag("Mosaic Censorship", "126"),
    Tag("Swinging", "127"), Tag("Voyeurism", "128"), Tag("Gender Change", "129"),
    Tag("Gyaru-Oh", "131"), Tag("Full Color", "132"), Tag("Huge Breasts", "133"),
    Tag("Cunnilingus", "136"), Tag("Females Only", "137"),
    Tag("FFF Threesome", "138"), Tag("Fingering", "139"),
    Tag("Schoolgirl Uniform", "140"), Tag("Facesitting", "141"),
    Tag("No Penetration", "143"), Tag("Tribadism", "146"),
    Tag("Animated", "147"), Tag("Comic", "148"), Tag("Sister", "150"),
    Tag("Dog", "151"), Tag("Hidden Sex", "152"), Tag("Big Penis", "156"),
    Tag("Paizuri", "157"), Tag("Beach", "160"), Tag("Bald", "161"),
    Tag("Bride", "163"), Tag("Collar", "165"), Tag("Daughter", "166"),
    Tag("Eye-Covering Bang", "167"), Tag("Hairy Armpits", "168"),
    Tag("Petplay", "169"), Tag("Ponytail", "171"), Tag("Slave", "172"),
    Tag("Twins", "173"), Tag("Coach", "174"), Tag("Gymshorts", "176"),
    Tag("Full Censorship", "177"), Tag("School Swimsuit", "178"),
    Tag("Sweating", "179"), Tag("Schoolboy Uniform", "182"),
    Tag("Drugs", "183"), Tag("Drunk", "184"), Tag("Fairy", "185"),
    Tag("Wings", "186"), Tag("Bloomers", "189"), Tag("Chikan", "190"),
    Tag("School Gym Uniform", "191"), Tag("Squirting", "192"),
    Tag("Rimjob", "193"), Tag("Vaginal Birth", "194"),
    Tag("Wolf Girl", "199"), Tag("Prostitution", "200"),
    Tag("Blowjob Face", "201"), Tag("Thick Eyebrows", "202"),
    Tag("Chastity Belt", "203"), Tag("Feminization", "204"),
    Tag("Public Use", "205"), Tag("Small Penis", "206"),
    Tag("Strap-On", "207"), Tag("Dickgirl on Female", "209"),
    Tag("Sole Dickgirl", "211"), Tag("Wolf Boy", "214"),
    Tag("Crossdressing", "215"), Tag("Elf", "220"),
    Tag("Facial Hair", "221"), Tag("Inflation", "222"),
    Tag("Old Man", "223"), Tag("Possession", "224"),
    Tag("Snuff", "226"), Tag("Smalldom", "227"), Tag("Tomgirl", "228"),
    Tag("Long Tongue", "231"), Tag("Monster Girl", "232"),
    Tag("Cowgirl", "234"), Tag("Leash", "235"), Tag("Tall Man", "236"),
    Tag("Marin Kitagawa", "237"), Tag("Horns", "240"), Tag("Blood", "245"),
    Tag("Makeup", "246"), Tag("Multiple Orgasms", "247"),
    Tag("Triple Penetration", "248"), Tag("Blackmail", "253"),
    Tag("Clit Stimulation", "254"), Tag("Nipple Stimulation", "255"),
    Tag("Asphyxiation", "258"), Tag("Lactation", "263"),
    Tag("Dickgirl on Male", "265"), Tag("Shemale", "267"),
    Tag("Sensei", "268"), Tag("Centaur", "270"), Tag("Orc", "271"),
    Tag("Tail", "272"), Tag("Slime Girl", "275"), Tag("Demon Girl", "277"),
    Tag("Multimouth Blowjob", "279"), Tag("Transformation", "280"),
    Tag("Cosplaying", "284"), Tag("Dickgirl on Dickgirl", "285"),
    Tag("Urination", "286"), Tag("Bunny Girl", "288"),
    Tag("Drill Hair", "289"), Tag("Inverted Nipples", "290"),
    Tag("Big Nipples", "292"), Tag("Ghost", "295"),
    Tag("Horse Cock", "296"), Tag("Ochako Uraraka", "300"),
    Tag("Maid", "301"), Tag("Phimosis", "303"), Tag("Classroom", "304"),
    Tag("High Heels", "311"), Tag("Body Writing", "312"),
    Tag("Breast Expansion", "313"), Tag("Quetzalcoatl", "314"),
    Tag("Nun", "320"), Tag("Handjob", "321"), Tag("Crown", "324"),
    Tag("Forced Exposure", "325"), Tag("Leotard", "330"),
    Tag("Chinese Dress", "333"), Tag("Hair Buns", "334"),
    Tag("Son Goku", "337"), Tag("Shimaidon", "341"),
    Tag("Lab Coat", "343"), Tag("Nurse", "344"), Tag("Demon", "346"),
    Tag("Priest", "347"), Tag("Males Only", "353"),
    Tag("Oppai Loli", "354"), Tag("First Person Perspective", "356"),
    Tag("Focus Blowjob", "357"), Tag("Exposed Clothing", "359"),
    Tag("Dog Girl", "362"), Tag("Military", "363"),
    Tag("Gang Rape", "364"), Tag("VTuber", "366"), Tag("Apron", "372"),
    Tag("Magical Girl", "377"), Tag("Corruption", "378"),
    Tag("Crotch Tattoo", "379"), Tag("Cum Swap", "380"),
    Tag("Cumflation", "383"), Tag("Sunglasses", "385"),
    Tag("Hood", "389"), Tag("Sumata", "390"),
    Tag("Thigh High Boots", "391"), Tag("Dark Nipples", "394"),
    Tag("Bodysuit", "395"), Tag("Goblin", "398"), Tag("Slime", "399"),
    Tag("Warrior", "400"), Tag("Unusual Teeth", "401"),
    Tag("Omorashi", "412"), Tag("Tail Plug", "413"),
    Tag("Ball Sucking", "415"), Tag("Multiple Paizuri", "416"),
    Tag("Alien", "422"), Tag("Cousin", "423"),
    Tag("Age Progression", "424"), Tag("Body Swap", "425"),
    Tag("Huge Penis", "426"), Tag("Alien Girl", "428"),
    Tag("Bathing Room", "429"), Tag("Milking", "431"),
    Tag("Prehensile Hair", "433"), Tag("Catboy", "445"),
    Tag("Masked Face", "446"), Tag("Shared Senses", "447"),
    Tag("Sheep Girl", "448"), Tag("Clothed Male Nude Female", "453"),
    Tag("Assjob", "456"), Tag("Gokkun", "461"), Tag("Tanlines", "462"),
    Tag("Onahole", "466"), Tag("Big Balls", "471"), Tag("Brother", "474"),
    Tag("Fox Girl", "477"), Tag("Hotpants", "480"), Tag("Niece", "481"),
    Tag("Midget", "487"), Tag("Butt Plug", "488"), Tag("Kunoichi", "489"),
    Tag("Prostate Massage", "491"), Tag("FFT Threesome", "492"),
    Tag("Weight Gain", "494"), Tag("Miko", "495"),
    Tag("Gold Ship", "497"), Tag("Android 18", "498"),
    Tag("Horse", "504"), Tag("Reimu Hakurei", "507"),
    Tag("Garter Belt", "509"), Tag("Big Clit", "510"),
    Tag("Soushuuhen", "513"), Tag("Aether", "515"), Tag("Onsen", "517"),
    Tag("Satono Diamond", "519"), Tag("Chloroform", "522"),
    Tag("Clothed Female Nude Male", "523"), Tag("Oil", "543"),
    Tag("Apparel Bukkake", "544"), Tag("Closed Eyes", "548"),
    Tag("Moral Degeneration", "549"), Tag("Shibari", "553"),
    Tag("Age Regression", "555"), Tag("Monster", "560"),
    Tag("Shikikan", "566"), Tag("Growth", "571"),
    Tag("Transparent Clothing", "573"), Tag("Underwater Sex", "574"),
    Tag("Grass Wonder", "576"), Tag("Kaguya", "578"),
    Tag("Double Vaginal", "588"), Tag("Nipple Piercing", "589"),
    Tag("Body Modification", "591"), Tag("Crying", "592"),
    Tag("Tracksuit", "582"), Tag("Scanmark", "584"),
    Tag("Tsunade", "595"), Tag("Mihono Bourbon", "597"),
    Tag("Kindergarten Uniform", "599"), Tag("Double Blowjob", "600"),
    Tag("Robin", "604"), Tag("Witch", "609"), Tag("Chi Chi", "613"),
    Tag("Producer", "615"), Tag("Kimono", "616"),
    Tag("Orgasm Denial", "617"), Tag("Vampire", "652"),
    Tag("Grandmother", "654"), Tag("Clone", "655"),
    Tag("MMM Threesome", "656"), Tag("Nipple Fuck", "657"),
).distinctBy { it.name.lowercase() }
