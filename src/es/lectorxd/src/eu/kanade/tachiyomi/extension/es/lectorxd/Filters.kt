package eu.kanade.tachiyomi.extension.es.lectorxd

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.JsonElement

internal object Filters {

    class Type :
        Filter.Select<String>(
            "Tipo",
            arrayOf(
                "Todos",
                "Manga",
                "Manhwa",
                "Manhua",
                "Novela",
                "One Shot",
            ),
        )

    class Status :
        Filter.Select<String>(
            "Estado",
            arrayOf(
                "Todos",
                "En emisión",
                "Completado",
            ),
        )

    class Demographic :
        Filter.Select<String>(
            "Demografía",
            arrayOf(
                "Todos",
                "Shounen",
                "Seinen",
                "Shoujo",
                "Josei",
            ),
        )

    class AdultContent :
        Filter.Select<String>(
            "Contenido +18",
            arrayOf(
                "Solo general",
                "Todo",
                "Solo +18",
            ),
        )

    class OrderBy :
        Filter.Select<String>(
            "Ordenar por",
            arrayOf(
                "Recientes",
                "Mejor valorados",
                "Más vistos",
                "Más gente leyendo",
                "Más gente por leer",
                "Más completados",
                "Más capítulos",
            ),
        )

    class Tag(
        val ids: List<String>,
        name: String,
    ) : Filter.CheckBox(name)

    class GenreGroup(
        letter: String,
        tags: List<Tag>,
    ) : Filter.Group<Tag>(
        letter,
        tags,
    )

    class GenresFilter(
        groups: List<GenreGroup>,
    ) : Filter.Group<GenreGroup>(
        "Géneros",
        groups,
    )

    val typeValues = arrayOf(
        null,
        "manga",
        "manhwa",
        "manhua",
        "novela",
        "one_shot",
    )

    val statusValues = arrayOf(
        null,
        "en_emision",
        "completado",
    )

    val demographicValues = arrayOf(
        null,
        "shounen",
        "seinen",
        "shoujo",
        "josei",
    )

    val adultContentValues = arrayOf(
        "safe",
        "all",
        "adult",
    )

    val orderByValues = arrayOf(
        "recent",
        "rating",
        "views",
        "leyendo",
        "por_leer",
        "completado",
        "chapters",
    )

    fun getFilterList(
        data: JsonElement?,
    ): FilterList = FilterList(
        Filter.Header("Filtros de LectorXD"),
        OrderBy(),
        Status(),
        Type(),
        Demographic(),
        AdultContent(),

        GenresFilter(
            groups = listOf(
                GenreGroup(
                    letter = "Grupo A",
                    tags = listOf(
                        Tag(
                            ids = listOf("177"),
                            name = "+15",
                        ),
                        Tag(
                            ids = listOf("200"),
                            name = "+18",
                        ),
                        Tag(
                            ids = listOf("369", "751", "1056"),
                            name = "4-Koma",
                        ),
                        Tag(
                            ids = listOf("1202", "255"),
                            name = "A todo color",
                        ),
                        Tag(
                            ids = listOf("677"),
                            name = "Abuso",
                        ),
                        Tag(
                            ids = listOf("636"),
                            name = "Abuso infantil",
                        ),
                        Tag(
                            ids = listOf("848"),
                            name = "Abuso psicológico",
                        ),
                        Tag(
                            ids = listOf("740"),
                            name = "Abuso sexual",
                        ),
                        Tag(
                            ids = listOf("53"),
                            name = "Academia",
                        ),
                        Tag(
                            ids = listOf("523", "483"),
                            name = "Academia de magia",
                        ),
                        Tag(
                            ids = listOf("23", "147", "207"),
                            name = "Acción",
                        ),
                        Tag(
                            ids = listOf("365"),
                            name = "Acoso",
                        ),
                        Tag(
                            ids = listOf("367"),
                            name = "Acoso escolar",
                        ),
                        Tag(
                            ids = listOf("1041"),
                            name = "Actividad sexual explícita",
                        ),
                        Tag(
                            ids = listOf("1262"),
                            name = "Actor",
                        ),
                        Tag(
                            ids = listOf("409"),
                            name = "Actuación",
                        ),
                        Tag(
                            ids = listOf("430", "1219"),
                            name = "Adaptación",
                        ),
                        Tag(
                            ids = listOf("841"),
                            name = "Adaptación de anime",
                        ),
                        Tag(
                            ids = listOf("433", "468"),
                            name = "Adaptación de novela",
                        ),
                        Tag(
                            ids = listOf("439", "1047"),
                            name = "Adaptación de novela ligera",
                        ),
                        Tag(
                            ids = listOf("630"),
                            name = "Adaptación de novela web",
                        ),
                        Tag(
                            ids = listOf("617"),
                            name = "Adaptación de videojuego",
                        ),
                        Tag(
                            ids = listOf("1158"),
                            name = "Adivinación",
                        ),
                        Tag(
                            ids = listOf("499"),
                            name = "Adolescencia",
                        ),
                        Tag(
                            ids = listOf("579"),
                            name = "Adopción",
                        ),
                        Tag(
                            ids = listOf("205", "287"),
                            name = "Adultos",
                        ),
                        Tag(
                            ids = listOf("25", "191", "208"),
                            name = "Aventura",
                        ),
                        Tag(
                            ids = listOf("1162"),
                            name = "Aficiones",
                        ),
                        Tag(
                            ids = listOf("596"),
                            name = "Afrodisíaco",
                        ),
                        Tag(
                            ids = listOf("347"),
                            name = "Agentes",
                        ),
                        Tag(
                            ids = listOf("337"),
                            name = "Agentes encubiertos",
                        ),
                        Tag(
                            ids = listOf("348"),
                            name = "Agentes secretos",
                        ),
                        Tag(
                            ids = listOf("318"),
                            name = "Agricultura",
                        ),
                        Tag(
                            ids = listOf("1112"),
                            name = "Aguas termales",
                        ),
                        Tag(
                            ids = listOf("1232"),
                            name = "Aire libre",
                        ),
                        Tag(
                            ids = listOf("875"),
                            name = "Aislamiento",
                        ),
                        Tag(
                            ids = listOf("1090"),
                            name = "Alcohol",
                        ),
                        Tag(
                            ids = listOf("788"),
                            name = "Alemania",
                        ),
                        Tag(
                            ids = listOf("219"),
                            name = "Aliado",
                        ),
                        Tag(
                            ids = listOf("606", "861", "1272"),
                            name = "Alienígenas",
                        ),
                        Tag(
                            ids = listOf("874"),
                            name = "Alpinismo",
                        ),
                        Tag(
                            ids = listOf("728"),
                            name = "Alquimia",
                        ),
                        Tag(
                            ids = listOf("1181"),
                            name = "Alta mar",
                        ),
                        Tag(
                            ids = listOf("1328"),
                            name = "Presente alternativo",
                        ),
                        Tag(
                            ids = listOf("419"),
                            name = "Amantes",
                        ),
                        Tag(
                            ids = listOf("1083"),
                            name = "Amigas a amantes",
                        ),
                        Tag(
                            ids = listOf("300"),
                            name = "Amigos a amantes",
                        ),
                        Tag(
                            ids = listOf("295"),
                            name = "Amigos con beneficios",
                        ),
                        Tag(
                            ids = listOf("44"),
                            name = "Amigos con derechos",
                        ),
                        Tag(
                            ids = listOf("22"),
                            name = "Amigos de la infancia",
                        ),
                        Tag(
                            ids = listOf("215"),
                            name = "Amigos",
                        ),
                        Tag(
                            ids = listOf("387"),
                            name = "Amistad",
                        ),
                        Tag(
                            ids = listOf("879"),
                            name = "Amnesia",
                        ),
                        Tag(
                            ids = listOf("569"),
                            name = "Amo y esclavo",
                        ),
                        Tag(
                            ids = listOf("755"),
                            name = "Amo y sirvienta",
                        ),
                        Tag(
                            ids = listOf("810"),
                            name = "Amo y sirviente",
                        ),
                        Tag(
                            ids = listOf("682"),
                            name = "Amos y sirvientes",
                        ),
                        Tag(
                            ids = listOf("1298"),
                            name = "Amor a fuego lento",
                        ),
                        Tag(
                            ids = listOf("302"),
                            name = "Amor a primera vista",
                        ),
                        Tag(
                            ids = listOf("1129"),
                            name = "Amor cómico",
                        ),
                        Tag(
                            ids = listOf("441"),
                            name = "Amor de chicos",
                        ),
                        Tag(
                            ids = listOf("529"),
                            name = "Amor de chicas",
                        ),
                        Tag(
                            ids = listOf("1160"),
                            name = "Amor devoto",
                        ),
                        Tag(
                            ids = listOf("1067"),
                            name = "Amor en el trabajo",
                        ),
                        Tag(
                            ids = listOf("1207"),
                            name = "Amor entre chicos (BL)",
                        ),
                        Tag(
                            ids = listOf("963"),
                            name = "Amor entre chicas",
                        ),
                        Tag(
                            ids = listOf("761"),
                            name = "Amor fingido",
                        ),
                        Tag(
                            ids = listOf("970"),
                            name = "Amor fingido a verdadero",
                        ),
                        Tag(
                            ids = listOf("460"),
                            name = "Amor lésbico",
                        ),
                        Tag(
                            ids = listOf("927"),
                            name = "Amor no confesado",
                        ),
                        Tag(
                            ids = listOf("304"),
                            name = "Amor no correspondido",
                        ),
                        Tag(
                            ids = listOf("995"),
                            name = "Amor obsesivo",
                        ),
                        Tag(
                            ids = listOf("539"),
                            name = "Amor platónico",
                        ),
                        Tag(
                            ids = listOf("1116"),
                            name = "Amor predestinado",
                        ),
                        Tag(
                            ids = listOf("864"),
                            name = "Amor prohibido",
                        ),
                        Tag(
                            ids = listOf("1176"),
                            name = "Amoríos juveniles",
                        ),
                        Tag(
                            ids = listOf("1253"),
                            name = "Análisis",
                        ),
                        Tag(
                            ids = listOf("662"),
                            name = "Androides",
                        ),
                        Tag(
                            ids = listOf("932"),
                            name = "Ángeles",
                        ),
                        Tag(
                            ids = listOf("1310"),
                            name = "Angst",
                        ),
                        Tag(
                            ids = listOf("307", "1221"),
                            name = "Animales",
                        ),
                        Tag(
                            ids = listOf("591", "593"),
                            name = "Animales antropomórficos",
                        ),
                        Tag(
                            ids = listOf("403"),
                            name = "Ansiedad social",
                        ),
                        Tag(
                            ids = listOf("509", "1258"),
                            name = "Antihéroe",
                        ),
                        Tag(
                            ids = listOf("249", "1320"),
                            name = "Antología",
                        ),
                        Tag(
                            ids = listOf("1060"),
                            name = "Antigua Roma",
                        ),
                        Tag(
                            ids = listOf("1005"),
                            name = "Antiguo Egipto",
                        ),
                        Tag(
                            ids = listOf("372"),
                            name = "Antropomórfico",
                        ),
                        Tag(
                            ids = listOf("1030"),
                            name = "Antropomorfismo",
                        ),
                        Tag(
                            ids = listOf("1099"),
                            name = "Antropomorfo",
                        ),
                        Tag(
                            ids = listOf("1074"),
                            name = "Años 1930",
                        ),
                        Tag(
                            ids = listOf("906"),
                            name = "Años 1980",
                        ),
                        Tag(
                            ids = listOf("904", "1132"),
                            name = "Años 1990",
                        ),
                        Tag(
                            ids = listOf("899"),
                            name = "Apartamento",
                        ),
                        Tag(
                            ids = listOf("240"),
                            name = "Aplicación",
                        ),
                        Tag(
                            ids = listOf("371"),
                            name = "Apocalipsis",
                        ),
                        Tag(
                            ids = listOf("384", "487"),
                            name = "Apocalipsis zombi",
                        ),
                        Tag(
                            ids = listOf("63", "162"),
                            name = "Apocalíptico",
                        ),
                        Tag(
                            ids = listOf("1128"),
                            name = "Aprendizaje",
                        ),
                        Tag(
                            ids = listOf("277"),
                            name = "Apuestas",
                        ),
                        Tag(
                            ids = listOf("467"),
                            name = "Aristocracia",
                        ),
                        Tag(
                            ids = listOf("1125"),
                            name = "Aristocracia / realeza",
                        ),
                        Tag(
                            ids = listOf("936"),
                            name = "Armas de fuego",
                        ),
                        Tag(
                            ids = listOf("654"),
                            name = "Arquería",
                        ),
                        Tag(
                            ids = listOf("852"),
                            name = "Arrepentimiento",
                        ),
                        Tag(
                            ids = listOf("938"),
                            name = "Arte",
                        ),
                        Tag(
                            ids = listOf("1080"),
                            name = "Artes escénicas",
                        ),
                        Tag(
                            ids = listOf("54", "1246", "224"),
                            name = "Artes marciales",
                        ),
                        Tag(
                            ids = listOf("917"),
                            name = "Artes plásticas",
                        ),
                        Tag(
                            ids = listOf("414"),
                            name = "Artesanía",
                        ),
                        Tag(
                            ids = listOf("634", "1006"),
                            name = "Asesinato",
                        ),
                        Tag(
                            ids = listOf("530"),
                            name = "Asesinos",
                        ),
                        Tag(
                            ids = listOf("724", "786"),
                            name = "Asesinos en serie",
                        ),
                        Tag(
                            ids = listOf("743"),
                            name = "Asia Central",
                        ),
                        Tag(
                            ids = listOf("1188"),
                            name = "Astronomía",
                        ),
                        Tag(
                            ids = listOf("592"),
                            name = "Atletismo",
                        ),
                        Tag(
                            ids = listOf("1215"),
                            name = "Atracción mutua",
                        ),
                        Tag(
                            ids = listOf("977"),
                            name = "Autobiográfico",
                        ),
                        Tag(
                            ids = listOf("464"),
                            name = "Autoestima",
                        ),
                        Tag(
                            ids = listOf("1033"),
                            name = "Autolesión",
                        ),
                        Tag(
                            ids = listOf("834"),
                            name = "Automóviles",
                        ),
                        Tag(
                            ids = listOf("914"),
                            name = "Automovilismo",
                        ),
                        Tag(
                            ids = listOf("135"),
                            name = "AV",
                        ),
                        Tag(
                            ids = listOf("1212"),
                            name = "Ganador de premios",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo B",
                    tags = listOf(
                        Tag(
                            ids = listOf("1055"),
                            name = "Bádminton",
                        ),
                        Tag(
                            ids = listOf("1113"),
                            name = "Baile",
                        ),
                        Tag(
                            ids = listOf("668"),
                            name = "Baloncesto",
                        ),
                        Tag(
                            ids = listOf("1194"),
                            name = "Banda",
                        ),
                        Tag(
                            ids = listOf("1054"),
                            name = "Banda musical",
                        ),
                        Tag(
                            ids = listOf("822", "1213"),
                            name = "Béisbol",
                        ),
                        Tag(
                            ids = listOf("638"),
                            name = "BDSM",
                        ),
                        Tag(
                            ids = listOf("154"),
                            name = "Bebés bonitos",
                        ),
                        Tag(
                            ids = listOf("373"),
                            name = "Bebidas alcohólicas",
                        ),
                        Tag(
                            ids = listOf("768"),
                            name = "Bélico",
                        ),
                        Tag(
                            ids = listOf("211"),
                            name = "Bestias",
                        ),
                        Tag(
                            ids = listOf("257"),
                            name = "Pechos grandes",
                        ),
                        Tag(
                            ids = listOf("62", "1206"),
                            name = "Boys Love",
                        ),
                        Tag(
                            ids = listOf("607"),
                            name = "Brujas",
                        ),
                        Tag(
                            ids = listOf("948"),
                            name = "Buceo",
                        ),
                        Tag(
                            ids = listOf("490"),
                            name = "Bucle temporal",
                        ),
                        Tag(
                            ids = listOf("368"),
                            name = "Bullying",
                        ),
                        Tag(
                            ids = listOf("846"),
                            name = "Burlas",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo C",
                    tags = listOf(
                        Tag(
                            ids = listOf("608"),
                            name = "Caballeros",
                        ),
                        Tag(
                            ids = listOf("900"),
                            name = "Caballos",
                        ),
                        Tag(
                            ids = listOf("840"),
                            name = "Cafetería",
                        ),
                        Tag(
                            ids = listOf("380", "982"),
                            name = "Calabozo",
                        ),
                        Tag(
                            ids = listOf("1117"),
                            name = "Caligrafía",
                        ),
                        Tag(
                            ids = listOf("767"),
                            name = "Cambiaformas",
                        ),
                        Tag(
                            ids = listOf("359"),
                            name = "Cambio de sexo",
                        ),
                        Tag(
                            ids = listOf("494"),
                            name = "Campamento",
                        ),
                        Tag(
                            ids = listOf("1231"),
                            name = "Camping",
                        ),
                        Tag(
                            ids = listOf("752"),
                            name = "Campo",
                        ),
                        Tag(
                            ids = listOf("241"),
                            name = "Campus",
                        ),
                        Tag(
                            ids = listOf("964"),
                            name = "Canibalismo",
                        ),
                        Tag(
                            ids = listOf("885"),
                            name = "Capítulo único",
                        ),
                        Tag(
                            ids = listOf("1184"),
                            name = "Capítulos cortos",
                        ),
                        Tag(
                            ids = listOf("590"),
                            name = "Carreras",
                        ),
                        Tag(
                            ids = listOf("937"),
                            name = "Casa compartida",
                        ),
                        Tag(
                            ids = listOf("971"),
                            name = "Casa de huéspedes",
                        ),
                        Tag(
                            ids = listOf("1123"),
                            name = "Casa embrujada",
                        ),
                        Tag(
                            ids = listOf("1259"),
                            name = "Casada/o",
                        ),
                        Tag(
                            ids = listOf("323"),
                            name = "Casado",
                        ),
                        Tag(
                            ids = listOf("1153"),
                            name = "Caza",
                        ),
                        Tag(
                            ids = listOf("271", "714"),
                            name = "Cazadores",
                        ),
                        Tag(
                            ids = listOf("505"),
                            name = "Cazarrecompensas",
                        ),
                        Tag(
                            ids = listOf("835"),
                            name = "Celebridades",
                        ),
                        Tag(
                            ids = listOf("820"),
                            name = "Celos",
                        ),
                        Tag(
                            ids = listOf("1336"),
                            name = "Chaebol",
                        ),
                        Tag(
                            ids = listOf("990"),
                            name = "Chamanismo",
                        ),
                        Tag(
                            ids = listOf("485", "1325"),
                            name = "Chantaje",
                        ),
                        Tag(
                            ids = listOf("385"),
                            name = "Chica a chica",
                        ),
                        Tag(
                            ids = listOf("1052"),
                            name = "Chica fría",
                        ),
                        Tag(
                            ids = listOf("1032"),
                            name = "Chica gigante",
                        ),
                        Tag(
                            ids = listOf("800"),
                            name = "Chica mágica",
                        ),
                        Tag(
                            ids = listOf("838"),
                            name = "Chicas con armas",
                        ),
                        Tag(
                            ids = listOf("828"),
                            name = "Chicas mágicas",
                        ),
                        Tag(
                            ids = listOf("344"),
                            name = "Chicas monstruo",
                        ),
                        Tag(
                            ids = listOf("926"),
                            name = "China",
                        ),
                        Tag(
                            ids = listOf("358"),
                            name = "China antigua",
                        ),
                        Tag(
                            ids = listOf("793"),
                            name = "China histórica",
                        ),
                        Tag(
                            ids = listOf("867"),
                            name = "Cibercafé",
                        ),
                        Tag(
                            ids = listOf("696", "720"),
                            name = "Ciberpunk",
                        ),
                        Tag(
                            ids = listOf("913"),
                            name = "Cíborgs",
                        ),
                        Tag(
                            ids = listOf("531"),
                            name = "Ciclismo",
                        ),
                        Tag(
                            ids = listOf("709"),
                            name = "Ciencia",
                        ),
                        Tag(
                            ids = listOf("55", "244", "1268"),
                            name = "Ciencia ficción",
                        ),
                        Tag(
                            ids = listOf("1108"),
                            name = "Cine",
                        ),
                        Tag(
                            ids = listOf("940"),
                            name = "Cinematografía",
                        ),
                        Tag(
                            ids = listOf("886"),
                            name = "Circo",
                        ),
                        Tag(
                            ids = listOf("1173"),
                            name = "Citas",
                        ),
                        Tag(
                            ids = listOf("766"),
                            name = "Clases particulares",
                        ),
                        Tag(
                            ids = listOf("671"),
                            name = "Clones",
                        ),
                        Tag(
                            ids = listOf("949"),
                            name = "Club",
                        ),
                        Tag(
                            ids = listOf("450"),
                            name = "Club escolar",
                        ),
                        Tag(
                            ids = listOf("397", "1216"),
                            name = "Cocina",
                        ),
                        Tag(
                            ids = listOf("1145"),
                            name = "Coexistencia",
                        ),
                        Tag(
                            ids = listOf("805"),
                            name = "Coexistencia / convivencia",
                        ),
                        Tag(
                            ids = listOf("250"),
                            name = "Cohabitación",
                        ),
                        Tag(
                            ids = listOf("1165"),
                            name = "Coinquilinos",
                        ),
                        Tag(
                            ids = listOf("28", "187"),
                            name = "Comedia",
                        ),
                        Tag(
                            ids = listOf("735"),
                            name = "Comedia erótica",
                        ),
                        Tag(
                            ids = listOf("526"),
                            name = "Comedia negra",
                        ),
                        Tag(
                            ids = listOf("293"),
                            name = "Comedia romántica",
                        ),
                        Tag(
                            ids = listOf("703"),
                            name = "Comercio",
                        ),
                        Tag(
                            ids = listOf("1245"),
                            name = "Cómic",
                        ),
                        Tag(
                            ids = listOf("792"),
                            name = "Cómic web",
                        ),
                        Tag(
                            ids = listOf("869"),
                            name = "Comida",
                        ),
                        Tag(
                            ids = listOf("473"),
                            name = "Comida y bebida",
                        ),
                        Tag(
                            ids = listOf("1297"),
                            name = "Coming of Age",
                        ),
                        Tag(
                            ids = listOf("497"),
                            name = "Compañeras de cuarto",
                        ),
                        Tag(
                            ids = listOf("893"),
                            name = "Compañerismo",
                        ),
                        Tag(
                            ids = listOf("1155"),
                            name = "Compañeros",
                        ),
                        Tag(
                            ids = listOf("478"),
                            name = "Compañeros de clase",
                        ),
                        Tag(
                            ids = listOf("1172"),
                            name = "Compañeros de equipo",
                        ),
                        Tag(
                            ids = listOf("1022"),
                            name = "Compañeros de piso",
                        ),
                        Tag(
                            ids = listOf("329"),
                            name = "Compañeros de trabajo",
                        ),
                        Tag(
                            ids = listOf("833"),
                            name = "Compañeros de viaje",
                        ),
                        Tag(
                            ids = listOf("679"),
                            name = "Competencia",
                        ),
                        Tag(
                            ids = listOf("1088"),
                            name = "Competición",
                        ),
                        Tag(
                            ids = listOf("957"),
                            name = "Compromiso",
                        ),
                        Tag(
                            ids = listOf("855"),
                            name = "Compromiso roto",
                        ),
                        Tag(
                            ids = listOf("1122"),
                            name = "Comunidad",
                        ),
                        Tag(
                            ids = listOf("716"),
                            name = "Confinamiento",
                        ),
                        Tag(
                            ids = listOf("692"),
                            name = "Consejo estudiantil",
                        ),
                        Tag(
                            ids = listOf("609"),
                            name = "Conspiración",
                        ),
                        Tag(
                            ids = listOf("1252"),
                            name = "Constelaciones",
                        ),
                        Tag(
                            ids = listOf("475"),
                            name = "Consumo de alcohol",
                        ),
                        Tag(
                            ids = listOf("928"),
                            name = "Consumo de tabaco",
                        ),
                        Tag(
                            ids = listOf("423"),
                            name = "Contemporáneo",
                        ),
                        Tag(
                            ids = listOf("1311"),
                            name = "Fantasía contemporánea",
                        ),
                        Tag(
                            ids = listOf("289"),
                            name = "Contenido adulto",
                        ),
                        Tag(
                            ids = listOf("850"),
                            name = "Contenido explícito",
                        ),
                        Tag(
                            ids = listOf("429"),
                            name = "Contenido para adultos",
                        ),
                        Tag(
                            ids = listOf("296"),
                            name = "Contenido sexual",
                        ),
                        Tag(
                            ids = listOf("291"),
                            name = "Contenido sexual explícito",
                        ),
                        Tag(
                            ids = listOf("564"),
                            name = "Contenido sexual leve",
                        ),
                        Tag(
                            ids = listOf("952"),
                            name = "Contenido sexual parcial",
                        ),
                        Tag(
                            ids = listOf("1350"),
                            name = "Contrato de matrimonio",
                        ),
                        Tag(
                            ids = listOf("1210"),
                            name = "Contratos de amor",
                        ),
                        Tag(
                            ids = listOf("715"),
                            name = "Control mental",
                        ),
                        Tag(
                            ids = listOf("251"),
                            name = "Convivencia",
                        ),
                        Tag(
                            ids = listOf("1316"),
                            name = "Policías",
                        ),
                        Tag(
                            ids = listOf("1073"),
                            name = "Corea",
                        ),
                        Tag(
                            ids = listOf("1147"),
                            name = "Corea colonial",
                        ),
                        Tag(
                            ids = listOf("532"),
                            name = "Corea del Sur",
                        ),
                        Tag(
                            ids = listOf("783"),
                            name = "Corea histórica",
                        ),
                        Tag(
                            ids = listOf("1023"),
                            name = "Corea tradicional",
                        ),
                        Tag(
                            ids = listOf("1313"),
                            name = "Corrupción",
                        ),
                        Tag(
                            ids = listOf("521"),
                            name = "Cosplay",
                        ),
                        Tag(
                            ids = listOf("689"),
                            name = "Costero",
                        ),
                        Tag(
                            ids = listOf("504"),
                            name = "Cotidiano",
                        ),
                        Tag(
                            ids = listOf("737"),
                            name = "Crecimiento personal",
                        ),
                        Tag(
                            ids = listOf("1095"),
                            name = "Criadas",
                        ),
                        Tag(
                            ids = listOf("472"),
                            name = "Crianza",
                        ),
                        Tag(
                            ids = listOf("1230"),
                            name = "Crianza de hijos",
                        ),
                        Tag(
                            ids = listOf("376"),
                            name = "Criaturas fantásticas",
                        ),
                        Tag(
                            ids = listOf("234", "1314"),
                            name = "Crimen",
                        ),
                        Tag(
                            ids = listOf("1038"),
                            name = "Crimen organizado",
                        ),
                        Tag(
                            ids = listOf("1254"),
                            name = "Criptomonedas",
                        ),
                        Tag(
                            ids = listOf("321", "1294"),
                            name = "Crossdressing",
                        ),
                        Tag(
                            ids = listOf("595"),
                            name = "Crossover",
                        ),
                        Tag(
                            ids = listOf("851"),
                            name = "Cuarentena",
                        ),
                        Tag(
                            ids = listOf("597", "746"),
                            name = "Cuentos de hadas",
                        ),
                        Tag(
                            ids = listOf("806"),
                            name = "Cuidado infantil",
                        ),
                        Tag(
                            ids = listOf("1347"),
                            name = "Cuidado de niños",
                        ),
                        Tag(
                            ids = listOf("263", "284"),
                            name = "Cultivación",
                        ),
                        Tag(
                            ids = listOf("60"),
                            name = "Cultivo",
                        ),
                        Tag(
                            ids = listOf("658"),
                            name = "Culto",
                        ),
                        Tag(
                            ids = listOf("1121"),
                            name = "Culto demoníaco",
                        ),
                        Tag(
                            ids = listOf("742"),
                            name = "Cultura tradicional",
                        ),
                        Tag(
                            ids = listOf("782"),
                            name = "Culturismo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo D",
                    tags = listOf(
                        Tag(
                            ids = listOf("731"),
                            name = "Danmei",
                        ),
                        Tag(
                            ids = listOf("1351"),
                            name = "De desconocidos a amantes",
                        ),
                        Tag(
                            ids = listOf("528", "729"),
                            name = "De enemigos a amantes",
                        ),
                        Tag(
                            ids = listOf("486"),
                            name = "De rivales a amantes",
                        ),
                        Tag(
                            ids = listOf("863"),
                            name = "Deidades",
                        ),
                        Tag(
                            ids = listOf("462"),
                            name = "Delincuencia",
                        ),
                        Tag(
                            ids = listOf("518", "190", "1284"),
                            name = "Delincuentes",
                        ),
                        Tag(
                            ids = listOf("179", "186", "196", "1264"),
                            name = "Demonios",
                        ),
                        Tag(
                            ids = listOf("164", "258"),
                            name = "Deportes",
                        ),
                        Tag(
                            ids = listOf("1064"),
                            name = "Deportes de combate",
                        ),
                        Tag(
                            ids = listOf("1203"),
                            name = "Deportes de contacto",
                        ),
                        Tag(
                            ids = listOf("942"),
                            name = "Deportes de equipo",
                        ),
                        Tag(
                            ids = listOf("620"),
                            name = "Depresión",
                        ),
                        Tag(
                            ids = listOf("764"),
                            name = "Desamor",
                        ),
                        Tag(
                            ids = listOf("641"),
                            name = "Desastres naturales",
                        ),
                        Tag(
                            ids = listOf("945"),
                            name = "Desempleo",
                        ),
                        Tag(
                            ids = listOf("1031"),
                            name = "Desmembramiento",
                        ),
                        Tag(
                            ids = listOf("489"),
                            name = "Desnudez",
                        ),
                        Tag(
                            ids = listOf("1066"),
                            name = "Desnudez parcial",
                        ),
                        Tag(
                            ids = listOf("1332"),
                            name = "Detective",
                        ),
                        Tag(
                            ids = listOf("640"),
                            name = "Detectives",
                        ),
                        Tag(
                            ids = listOf("962"),
                            name = "Detención del tiempo",
                        ),
                        Tag(
                            ids = listOf("687"),
                            name = "Deudas",
                        ),
                        Tag(
                            ids = listOf("691", "865"),
                            name = "Diferencia de altura",
                        ),
                        Tag(
                            ids = listOf("324"),
                            name = "Diferencia de edad",
                        ),
                        Tag(
                            ids = listOf("1130"),
                            name = "Diferencia de tamaño",
                        ),
                        Tag(
                            ids = listOf("816"),
                            name = "Dimensión alternativa",
                        ),
                        Tag(
                            ids = listOf("1169"),
                            name = "Dinero",
                        ),
                        Tag(
                            ids = listOf("1133"),
                            name = "Dinosaurios",
                        ),
                        Tag(
                            ids = listOf("615"),
                            name = "Dioses",
                        ),
                        Tag(
                            ids = listOf("575"),
                            name = "Discapacidad",
                        ),
                        Tag(
                            ids = listOf("1077"),
                            name = "Discapacidad auditiva",
                        ),
                        Tag(
                            ids = listOf("1086"),
                            name = "Discriminación",
                        ),
                        Tag(
                            ids = listOf("611"),
                            name = "Distopía",
                        ),
                        Tag(
                            ids = listOf("1199"),
                            name = "Divorcio",
                        ),
                        Tag(
                            ids = listOf("771"),
                            name = "Doble vida",
                        ),
                        Tag(
                            ids = listOf("19", "402"),
                            name = "Dōjinshi",
                        ),
                        Tag(
                            ids = listOf("1256"),
                            name = "Dominación",
                        ),
                        Tag(
                            ids = listOf("1004"),
                            name = "Doppelgänger",
                        ),
                        Tag(
                            ids = listOf("496"),
                            name = "Dormitorio escolar",
                        ),
                        Tag(
                            ids = listOf("471"),
                            name = "Dragones",
                        ),
                        Tag(
                            ids = listOf("30"),
                            name = "Drama",
                        ),
                        Tag(
                            ids = listOf("854"),
                            name = "Drama psicológico",
                        ),
                        Tag(
                            ids = listOf("1062"),
                            name = "Drogas",
                        ),
                        Tag(
                            ids = listOf("931"),
                            name = "Duelo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo E",
                    tags = listOf(
                        Tag(
                            ids = listOf("1352"),
                            name = "Ebriedad",
                        ),
                        Tag(
                            ids = listOf("33"),
                            name = "Ecchi",
                        ),
                        Tag(
                            ids = listOf("637"),
                            name = "Economía",
                        ),
                        Tag(
                            ids = listOf("1037"),
                            name = "Educativo",
                        ),
                        Tag(
                            ids = listOf("907"),
                            name = "Egipto",
                        ),
                        Tag(
                            ids = listOf("40"),
                            name = "Ejército",
                        ),
                        Tag(
                            ids = listOf("656"),
                            name = "Elfos",
                        ),
                        Tag(
                            ids = listOf("727"),
                            name = "Embarazo",
                        ),
                        Tag(
                            ids = listOf("198"),
                            name = "Emperor Scan",
                        ),
                        Tag(
                            ids = listOf("749"),
                            name = "Empleada doméstica",
                        ),
                        Tag(
                            ids = listOf("501", "1286"),
                            name = "En blanco y negro",
                        ),
                        Tag(
                            ids = listOf("1260"),
                            name = "En curso",
                        ),
                        Tag(
                            ids = listOf("492"),
                            name = "Enamoramiento",
                        ),
                        Tag(
                            ids = listOf("723"),
                            name = "Enamoramiento no correspondido",
                        ),
                        Tag(
                            ids = listOf("1126"),
                            name = "Enamoramiento obsesivo / devoción",
                        ),
                        Tag(
                            ids = listOf("1142"),
                            name = "Enfermedad",
                        ),
                        Tag(
                            ids = listOf("858"),
                            name = "Engaño",
                        ),
                        Tag(
                            ids = listOf("341"),
                            name = "Entorno laboral",
                        ),
                        Tag(
                            ids = listOf("699"),
                            name = "Entrenamiento",
                        ),
                        Tag(
                            ids = listOf("968"),
                            name = "Entretenimiento",
                        ),
                        Tag(
                            ids = listOf("1183"),
                            name = "Episódico",
                        ),
                        Tag(
                            ids = listOf("814"),
                            name = "Época actual",
                        ),
                        Tag(
                            ids = listOf("411"),
                            name = "Época contemporánea",
                        ),
                        Tag(
                            ids = listOf("1118"),
                            name = "Época histórica",
                        ),
                        Tag(
                            ids = listOf("985"),
                            name = "Época medieval",
                        ),
                        Tag(
                            ids = listOf("690"),
                            name = "Época victoriana",
                        ),
                        Tag(
                            ids = listOf("426"),
                            name = "Erótico",
                        ),
                        Tag(
                            ids = listOf("567"),
                            name = "Esclavitud",
                        ),
                        Tag(
                            ids = listOf("165", "1217"),
                            name = "Escolar",
                        ),
                        Tag(
                            ids = listOf("236"),
                            name = "Escuela",
                        ),
                        Tag(
                            ids = listOf("483"),
                            name = "Escuela de magia",
                        ),
                        Tag(
                            ids = listOf("360"),
                            name = "Escuela secundaria",
                        ),
                        Tag(
                            ids = listOf("672"),
                            name = "Espacial",
                        ),
                        Tag(
                            ids = listOf("621"),
                            name = "Espacio",
                        ),
                        Tag(
                            ids = listOf("993"),
                            name = "Espacio exterior",
                        ),
                        Tag(
                            ids = listOf("987", "718"),
                            name = "Espadachines",
                        ),
                        Tag(
                            ids = listOf("830"),
                            name = "Espadas",
                        ),
                        Tag(
                            ids = listOf("278"),
                            name = "Español",
                        ),
                        Tag(
                            ids = listOf("784"),
                            name = "Espectáculo",
                        ),
                        Tag(
                            ids = listOf("1148"),
                            name = "Espers",
                        ),
                        Tag(
                            ids = listOf("700"),
                            name = "Espías",
                        ),
                        Tag(
                            ids = listOf("860"),
                            name = "Espionaje",
                        ),
                        Tag(
                            ids = listOf("704"),
                            name = "Espíritus",
                        ),
                        Tag(
                            ids = listOf("1100"),
                            name = "Esposos",
                        ),
                        Tag(
                            ids = listOf("902"),
                            name = "Estados Unidos",
                        ),
                        Tag(
                            ids = listOf("500"),
                            name = "Estrategia",
                        ),
                        Tag(
                            ids = listOf("955"),
                            name = "Estrategia militar",
                        ),
                        Tag(
                            ids = listOf("779", "1238"),
                            name = "Estudiante y profesor",
                        ),
                        Tag(
                            ids = listOf("1285"),
                            name = "Estudiantes",
                        ),
                        Tag(
                            ids = listOf("541"),
                            name = "Europa",
                        ),
                        Tag(
                            ids = listOf("1115"),
                            name = "Europa medieval",
                        ),
                        Tag(
                            ids = listOf("184"),
                            name = "Evolución",
                        ),
                        Tag(
                            ids = listOf("753", "785"),
                            name = "Expareja",
                        ),
                        Tag(
                            ids = listOf("1250"),
                            name = "Exclusivo",
                        ),
                        Tag(
                            ids = listOf("939"),
                            name = "Exhibicionismo",
                        ),
                        Tag(
                            ids = listOf("563"),
                            name = "Exorcismo",
                        ),
                        Tag(
                            ids = listOf("659"),
                            name = "Exorcistas",
                        ),
                        Tag(
                            ids = listOf("565"),
                            name = "Expulsión",
                        ),
                        Tag(
                            ids = listOf("707"),
                            name = "Expulsión del grupo",
                        ),
                        Tag(
                            ids = listOf("1306"),
                            name = "Extranjeros",
                        ),
                        Tag(
                            ids = listOf("545"),
                            name = "Extraterrestres",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo F",
                    tags = listOf(
                        Tag(
                            ids = listOf("1159"),
                            name = "Falso matrimonio",
                        ),
                        Tag(
                            ids = listOf("1195"),
                            name = "Falso romance",
                        ),
                        Tag(
                            ids = listOf("199"),
                            name = "Familia",
                        ),
                        Tag(
                            ids = listOf("1333"),
                            name = "Familia adoptiva o encontrada",
                        ),
                        Tag(
                            ids = listOf("676"),
                            name = "Familia disfuncional",
                        ),
                        Tag(
                            ids = listOf("598"),
                            name = "Familia encontrada",
                        ),
                        Tag(
                            ids = listOf("1290"),
                            name = "Familia ensamblada",
                        ),
                        Tag(
                            ids = listOf("516"),
                            name = "Familia política",
                        ),
                        Tag(
                            ids = listOf("1025"),
                            name = "Familia tóxica",
                        ),
                        Tag(
                            ids = listOf("338"),
                            name = "Familiar",
                        ),
                        Tag(
                            ids = listOf("364"),
                            name = "Familiares",
                        ),
                        Tag(
                            ids = listOf("765"),
                            name = "Familiares lejanos",
                        ),
                        Tag(
                            ids = listOf("811"),
                            name = "Famosos",
                        ),
                        Tag(
                            ids = listOf("343"),
                            name = "Fanservice",
                        ),
                        Tag(
                            ids = listOf("24", "160", "189"),
                            name = "Fantasía",
                        ),
                        Tag(
                            ids = listOf("774"),
                            name = "Fantasía contemporánea",
                        ),
                        Tag(
                            ids = listOf("619"),
                            name = "Fantasía europea",
                        ),
                        Tag(
                            ids = listOf("796"),
                            name = "Fantasía histórica",
                        ),
                        Tag(
                            ids = listOf("491"),
                            name = "Fantasía medieval",
                        ),
                        Tag(
                            ids = listOf("1282"),
                            name = "Fantasía moderna",
                        ),
                        Tag(
                            ids = listOf("451"),
                            name = "Fantasía oriental",
                        ),
                        Tag(
                            ids = listOf("395"),
                            name = "Fantasía oscura",
                        ),
                        Tag(
                            ids = listOf("510"),
                            name = "Fantasía urbana",
                        ),
                        Tag(
                            ids = listOf("562", "1292"),
                            name = "Fantasmas",
                        ),
                        Tag(
                            ids = listOf("1309"),
                            name = "Mundo de fantasía",
                        ),
                        Tag(
                            ids = listOf("534"),
                            name = "Farándula",
                        ),
                        Tag(
                            ids = listOf("857"),
                            name = "Femdom",
                        ),
                        Tag(
                            ids = listOf("1114", "1348"),
                            name = "Fetiches",
                        ),
                        Tag(
                            ids = listOf("730"),
                            name = "Fetichismo",
                        ),
                        Tag(
                            ids = listOf("550"),
                            name = "Filosofía",
                        ),
                        Tag(
                            ids = listOf("275", "1345"),
                            name = "Filosófico",
                        ),
                        Tag(
                            ids = listOf("1263"),
                            name = "Finalizado",
                        ),
                        Tag(
                            ids = listOf("325"),
                            name = "Fitness",
                        ),
                        Tag(
                            ids = listOf("973"),
                            name = "Fitness y dieta",
                        ),
                        Tag(
                            ids = listOf("335"),
                            name = "Fobia",
                        ),
                        Tag(
                            ids = listOf("604", "1096"),
                            name = "Folclore",
                        ),
                        Tag(
                            ids = listOf("1205"),
                            name = "Formato vertical",
                        ),
                        Tag(
                            ids = listOf("1189"),
                            name = "Fotografía",
                        ),
                        Tag(
                            ids = listOf("547"),
                            name = "Francia",
                        ),
                        Tag(
                            ids = listOf("1196"),
                            name = "Fuga",
                        ),
                        Tag(
                            ids = listOf("933"),
                            name = "Fugitiva",
                        ),
                        Tag(
                            ids = listOf("584"),
                            name = "Fútbol",
                        ),
                        Tag(
                            ids = listOf("436"),
                            name = "Futurista",
                        ),
                        Tag(
                            ids = listOf("721"),
                            name = "Futuro",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo G",
                    tags = listOf(
                        Tag(
                            ids = listOf("791", "1318"),
                            name = "Gags",
                        ),
                        Tag(
                            ids = listOf("941"),
                            name = "Gap moe",
                        ),
                        Tag(
                            ids = listOf("396"),
                            name = "Gastronomía",
                        ),
                        Tag(
                            ids = listOf("1174"),
                            name = "Gekiga",
                        ),
                        Tag(
                            ids = listOf("626"),
                            name = "Gemelas",
                        ),
                        Tag(
                            ids = listOf("506", "1208", "1235", "1269", "1293"),
                            name = "Cambio de género",
                        ),
                        Tag(
                            ids = listOf("1249"),
                            name = "Genio",
                        ),
                        Tag(
                            ids = listOf("303"),
                            name = "Gimnasio",
                        ),
                        Tag(
                            ids = listOf("61"),
                            name = "Girls Love",
                        ),
                        Tag(
                            ids = listOf("624"),
                            name = "Gladiadores",
                        ),
                        Tag(
                            ids = listOf("972"),
                            name = "Gorditas / BBW",
                        ),
                        Tag(
                            ids = listOf("57"),
                            name = "Gore",
                        ),
                        Tag(
                            ids = listOf("1330"),
                            name = "Gótico",
                        ),
                        Tag(
                            ids = listOf("698"),
                            name = "Grecia",
                        ),
                        Tag(
                            ids = listOf("965", "966"),
                            name = "Gremios",
                        ),
                        Tag(
                            ids = listOf("1034"),
                            name = "Guardaespaldas",
                        ),
                        Tag(
                            ids = listOf("1138"),
                            name = "Guardaespaldas y protegido",
                        ),
                        Tag(
                            ids = listOf("197"),
                            name = "Guerra",
                        ),
                        Tag(
                            ids = listOf("361"),
                            name = "Gyaru",
                        ),
                        Tag(
                            ids = listOf("756"),
                            name = "Gyeongseong",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo H",
                    tags = listOf(
                        Tag(
                            ids = listOf("881"),
                            name = "Habilidad especial",
                        ),
                        Tag(
                            ids = listOf("683"),
                            name = "Habilidad oculta",
                        ),
                        Tag(
                            ids = listOf("706"),
                            name = "Habilidades especiales",
                        ),
                        Tag(
                            ids = listOf("944"),
                            name = "Hacker",
                        ),
                        Tag(
                            ids = listOf("21", "237", "1275"),
                            name = "Harén",
                        ),
                        Tag(
                            ids = listOf("809"),
                            name = "Harén heterosexual",
                        ),
                        Tag(
                            ids = listOf("243", "1079", "246"),
                            name = "Harén inverso",
                        ),
                        Tag(
                            ids = listOf("285"),
                            name = "Hentai",
                        ),
                        Tag(
                            ids = listOf("334"),
                            name = "Hermanas",
                        ),
                        Tag(
                            ids = listOf("288"),
                            name = "Hermanastros",
                        ),
                        Tag(
                            ids = listOf("916"),
                            name = "Hermanos",
                        ),
                        Tag(
                            ids = listOf("261"),
                            name = "Hermes",
                        ),
                        Tag(
                            ids = listOf("583"),
                            name = "Héroe",
                        ),
                        Tag(
                            ids = listOf("1340"),
                            name = "Héroe y villano",
                        ),
                        Tag(
                            ids = listOf("206"),
                            name = "Herrería",
                        ),
                        Tag(
                            ids = listOf("167", "442"),
                            name = "Heterosexual",
                        ),
                        Tag(
                            ids = listOf("1326"),
                            name = "Escuela secundaria",
                        ),
                        Tag(
                            ids = listOf("363"),
                            name = "Hipnosis",
                        ),
                        Tag(
                            ids = listOf("1098"),
                            name = "Histeria colectiva",
                        ),
                        Tag(
                            ids = listOf("149"),
                            name = "Historia",
                        ),
                        Tag(
                            ids = listOf("45"),
                            name = "Historias cortas",
                        ),
                        Tag(
                            ids = listOf("222", "225", "248"),
                            name = "Histórico",
                        ),
                        Tag(
                            ids = listOf("884"),
                            name = "Histórico alternativo",
                        ),
                        Tag(
                            ids = listOf("1266"),
                            name = "Histórico europeo",
                        ),
                        Tag(
                            ids = listOf("538"),
                            name = "Histórico ficticio",
                        ),
                        Tag(
                            ids = listOf("1092"),
                            name = "Histórico oriental",
                        ),
                        Tag(
                            ids = listOf("1222"),
                            name = "Histórico reciente",
                        ),
                        Tag(
                            ids = listOf("750"),
                            name = "Hogar",
                        ),
                        Tag(
                            ids = listOf("1000"),
                            name = "Hokkaido",
                        ),
                        Tag(
                            ids = listOf("469"),
                            name = "Hombres bestia",
                        ),
                        Tag(
                            ids = listOf("65"),
                            name = "Hombres lobo",
                        ),
                        Tag(
                            ids = listOf("262"),
                            name = "Honeytoon",
                        ),
                        Tag(
                            ids = listOf("871"),
                            name = "Hong Kong",
                        ),
                        Tag(
                            ids = listOf("56"),
                            name = "Horror",
                        ),
                        Tag(
                            ids = listOf("555"),
                            name = "Horror corporal",
                        ),
                        Tag(
                            ids = listOf("556"),
                            name = "Horror cósmico",
                        ),
                        Tag(
                            ids = listOf("1021"),
                            name = "Horror psicológico",
                        ),
                        Tag(
                            ids = listOf("812"),
                            name = "Hospital",
                        ),
                        Tag(
                            ids = listOf("610"),
                            name = "Huérfanos",
                        ),
                        Tag(
                            ids = listOf("1257"),
                            name = "Humillación",
                        ),
                        Tag(
                            ids = listOf("685"),
                            name = "Humor absurdo",
                        ),
                        Tag(
                            ids = listOf("853"),
                            name = "Humor negro",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo I",
                    tags = listOf(
                        Tag(
                            ids = listOf("610"),
                            name = "Huérfanos",
                        ),
                        Tag(
                            ids = listOf("1257"),
                            name = "Humillación",
                        ),
                        Tag(
                            ids = listOf("685"),
                            name = "Humor absurdo",
                        ),
                        Tag(
                            ids = listOf("853"),
                            name = "Humor negro",
                        ),
                        Tag(
                            ids = listOf("1175"),
                            name = "Identidad de género",
                        ),
                        Tag(
                            ids = listOf("434"),
                            name = "Identidad secreta",
                        ),
                        Tag(
                            ids = listOf("920"),
                            name = "Identidad suplantada",
                        ),
                        Tag(
                            ids = listOf("868", "366"),
                            name = "Idol",
                        ),
                        Tag(
                            ids = listOf("1171"),
                            name = "Iglesia",
                        ),
                        Tag(
                            ids = listOf("981"),
                            name = "Imperio",
                        ),
                        Tag(
                            ids = listOf("645", "1223"),
                            name = "Incesto",
                        ),
                        Tag(
                            ids = listOf("404"),
                            name = "Incomodidad social",
                        ),
                        Tag(
                            ids = listOf("503"),
                            name = "Infancia",
                        ),
                        Tag(
                            ids = listOf("322"),
                            name = "Infidelidad",
                        ),
                        Tag(
                            ids = listOf("1197"),
                            name = "Infierno",
                        ),
                        Tag(
                            ids = listOf("512"),
                            name = "Inframundo",
                        ),
                        Tag(
                            ids = listOf("908"),
                            name = "Inglaterra",
                        ),
                        Tag(
                            ids = listOf("389"),
                            name = "Inmortalidad",
                        ),
                        Tag(
                            ids = listOf("628", "629"),
                            name = "Insinuación sexual",
                        ),
                        Tag(
                            ids = listOf("1236"),
                            name = "Insomnio",
                        ),
                        Tag(
                            ids = listOf("463"),
                            name = "Instituto",
                        ),
                        Tag(
                            ids = listOf("410"),
                            name = "Inteligencia artificial",
                        ),
                        Tag(
                            ids = listOf("421"),
                            name = "Intercambio de cuerpos",
                        ),
                        Tag(
                            ids = listOf("305"),
                            name = "Intercambio de identidad",
                        ),
                        Tag(
                            ids = listOf("144"),
                            name = "Intercambio de parejas",
                        ),
                        Tag(
                            ids = listOf("801"),
                            name = "Internet",
                        ),
                        Tag(
                            ids = listOf("856"),
                            name = "Inversión de roles",
                        ),
                        Tag(
                            ids = listOf("437"),
                            name = "Investigación",
                        ),
                        Tag(
                            ids = listOf("787"),
                            name = "Investigación policial",
                        ),
                        Tag(
                            ids = listOf("517", "829"),
                            name = "Invocación",
                        ),
                        Tag(
                            ids = listOf("821"),
                            name = "Invocación a otro mundo",
                        ),
                        Tag(
                            ids = listOf("272"),
                            name = "Invocador",
                        ),
                        Tag(
                            ids = listOf("47", "203"),
                            name = "Isekai",
                        ),
                        Tag(
                            ids = listOf("432"),
                            name = "Isekai inverso",
                        ),
                        Tag(
                            ids = listOf("770"),
                            name = "Isla",
                        ),
                        Tag(
                            ids = listOf("635"),
                            name = "Isla aislada",
                        ),
                        Tag(
                            ids = listOf("646"),
                            name = "Isla desierta",
                        ),
                        Tag(
                            ids = listOf("1105"),
                            name = "Islas",
                        ),
                        Tag(
                            ids = listOf("903"),
                            name = "Italia",
                        ),
                        Tag(
                            ids = listOf("1179"),
                            name = "Iyashikei",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo J",
                    tags = listOf(
                        Tag(
                            ids = listOf("388", "1327"),
                            name = "Japón",
                        ),
                        Tag(
                            ids = listOf("488"),
                            name = "Japón contemporáneo",
                        ),
                        Tag(
                            ids = listOf("417"),
                            name = "Japón feudal",
                        ),
                        Tag(
                            ids = listOf("795"),
                            name = "Japón moderno",
                        ),
                        Tag(
                            ids = listOf("632"),
                            name = "Japón rural",
                        ),
                        Tag(
                            ids = listOf("1097"),
                            name = "Japón tradicional",
                        ),
                        Tag(
                            ids = listOf("481"),
                            name = "Jefe y empleado",
                        ),
                        Tag(
                            ids = listOf("997", "1227", "1234"),
                            name = "Jefe y subordinada",
                        ),
                        Tag(
                            ids = listOf("502"),
                            name = "Jefe y subordinado",
                        ),
                        Tag(
                            ids = listOf("202"),
                            name = "Josei",
                        ),
                        Tag(
                            ids = listOf("705", "1010", "1050", "1051"),
                            name = "Juego de muerte",
                        ),
                        Tag(
                            ids = listOf("999", "600"),
                            name = "Juego de supervivencia",
                        ),
                        Tag(
                            ids = listOf("912"),
                            name = "Juego mortal",
                        ),
                        Tag(
                            ids = listOf("276"),
                            name = "Juegos",
                        ),
                        Tag(
                            ids = listOf("891"),
                            name = "Juegos de apuestas",
                        ),
                        Tag(
                            ids = listOf("479"),
                            name = "Juegos de azar",
                        ),
                        Tag(
                            ids = listOf("1131"),
                            name = "Juegos de cartas",
                        ),
                        Tag(
                            ids = listOf("1008"),
                            name = "Juegos mentales",
                        ),
                        Tag(
                            ids = listOf("1343"),
                            name = "Juujin",
                        ),
                        Tag(
                            ids = listOf("843"),
                            name = "Juvenil",
                        ),
                        Tag(
                            ids = listOf("507"),
                            name = "Juventud",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo K",
                    tags = listOf(
                        Tag(
                            ids = listOf("670", "1139"),
                            name = "Kemomimi",
                        ),
                        Tag(
                            ids = listOf("559"),
                            name = "Kouhai",
                        ),
                        Tag(
                            ids = listOf("804", "918"),
                            name = "Kouhai y Senpai",
                        ),
                        Tag(
                            ids = listOf("872"),
                            name = "Kowloon Walled City",
                        ),
                        Tag(
                            ids = listOf("599"),
                            name = "Kuudere",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo L",
                    tags = listOf(
                        Tag(
                            ids = listOf("1069"),
                            name = "Laboral",
                        ),
                        Tag(
                            ids = listOf("1149"),
                            name = "Lésbica",
                        ),
                        Tag(
                            ids = listOf("1141"),
                            name = "Leyendas urbanas",
                        ),
                        Tag(
                            ids = listOf("1334"),
                            name = "Leyes",
                        ),
                        Tag(
                            ids = listOf("309"),
                            name = "LGBTQ+",
                        ),
                        Tag(
                            ids = listOf("587", "827"),
                            name = "Representación LGBTQ",
                        ),
                        Tag(
                            ids = listOf("1300"),
                            name = "Libro de arte / guía",
                        ),
                        Tag(
                            ids = listOf("1143"),
                            name = "Literatura",
                        ),
                        Tag(
                            ids = listOf("1251"),
                            name = "Live action",
                        ),
                        Tag(
                            ids = listOf("554"),
                            name = "Locura",
                        ),
                        Tag(
                            ids = listOf("286"),
                            name = "Long Strip",
                        ),
                        Tag(
                            ids = listOf("773"),
                            name = "Lovecraftiano",
                        ),
                        Tag(
                            ids = listOf("943"),
                            name = "Lucha",
                        ),
                        Tag(
                            ids = listOf("1103"),
                            name = "Lucha clandestina",
                        ),
                        Tag(
                            ids = listOf("407"),
                            name = "Lucha libre",
                        ),
                        Tag(
                            ids = listOf("328"),
                            name = "Lugar de trabajo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo M",
                    tags = listOf(
                        Tag(
                            ids = listOf("39"),
                            name = "Madrastra",
                        ),
                        Tag(
                            ids = listOf("1161"),
                            name = "Madrastra e hijastro",
                        ),
                        Tag(
                            ids = listOf("32"),
                            name = "Madre e hija",
                        ),
                        Tag(
                            ids = listOf("647"),
                            name = "Madre e hijo",
                        ),
                        Tag(
                            ids = listOf("1101"),
                            name = "Madre soltera",
                        ),
                        Tag(
                            ids = listOf("148", "252", "923", "282"),
                            name = "Maduro",
                        ),
                        Tag(
                            ids = listOf("456", "447"),
                            name = "Maestro y discípulo",
                        ),
                        Tag(
                            ids = listOf("826"),
                            name = "Maestro y aprendiz",
                        ),
                        Tag(
                            ids = listOf("377"),
                            name = "Mafia",
                        ),
                        Tag(
                            ids = listOf("48", "1248"),
                            name = "Magia",
                        ),
                        Tag(
                            ids = listOf("1338"),
                            name = "Chicas mágicas",
                        ),
                        Tag(
                            ids = listOf("697"),
                            name = "Mahjong",
                        ),
                        Tag(
                            ids = listOf("1192", "1102"),
                            name = "Maid",
                        ),
                        Tag(
                            ids = listOf("553", "1012"),
                            name = "Maldición",
                        ),
                        Tag(
                            ids = listOf("356", "586"),
                            name = "Malentendido",
                        ),
                        Tag(
                            ids = listOf("264"),
                            name = "Manga",
                        ),
                        Tag(
                            ids = listOf("458"),
                            name = "Manga web",
                        ),
                        Tag(
                            ids = listOf("266"),
                            name = "Mangatoon",
                        ),
                        Tag(
                            ids = listOf("242"),
                            name = "MangoScan",
                        ),
                        Tag(
                            ids = listOf("173"),
                            name = "Manhua",
                        ),
                        Tag(
                            ids = listOf("182", "1241"),
                            name = "Manhwa",
                        ),
                        Tag(
                            ids = listOf("1072"),
                            name = "Manipulación",
                        ),
                        Tag(
                            ids = listOf("1335"),
                            name = "Manipulación del tiempo",
                        ),
                        Tag(
                            ids = listOf("1198"),
                            name = "Manipulación emocional",
                        ),
                        Tag(
                            ids = listOf("1059"),
                            name = "Manipulación psicológica",
                        ),
                        Tag(
                            ids = listOf("681"),
                            name = "Mansión",
                        ),
                        Tag(
                            ids = listOf("1104"),
                            name = "Mar",
                        ),
                        Tag(
                            ids = listOf("887"),
                            name = "Marionetas",
                        ),
                        Tag(
                            ids = listOf("622"),
                            name = "Marte",
                        ),
                        Tag(
                            ids = listOf("477"),
                            name = "Mascotas",
                        ),
                        Tag(
                            ids = listOf("445"),
                            name = "Masoquismo",
                        ),
                        Tag(
                            ids = listOf("571"),
                            name = "Maternidad",
                        ),
                        Tag(
                            ids = listOf("306"),
                            name = "Matrimonio",
                        ),
                        Tag(
                            ids = listOf("660", "762"),
                            name = "Matrimonio arreglado",
                        ),
                        Tag(
                            ids = listOf("702", "819"),
                            name = "Matrimonio falso",
                        ),
                        Tag(
                            ids = listOf("1107"),
                            name = "Matrimonio feliz",
                        ),
                        Tag(
                            ids = listOf("314"),
                            name = "Matrimonio por contrato",
                        ),
                        Tag(
                            ids = listOf("930"),
                            name = "Matrimonio por conveniencia",
                        ),
                        Tag(
                            ids = listOf("815"),
                            name = "Matrimonio secreto",
                        ),
                        Tag(
                            ids = listOf("1058"),
                            name = "Mayordomos",
                        ),
                        Tag(
                            ids = listOf("823"),
                            name = "Mayoría de edad",
                        ),
                        Tag(
                            ids = listOf("480", "455"),
                            name = "Mazmorra",
                        ),
                        Tag(
                            ids = listOf("218"),
                            name = "MC",
                        ),
                        Tag(
                            ids = listOf("226"),
                            name = "MCOP",
                        ),
                        Tag(
                            ids = listOf("245"),
                            name = "Mecha",
                        ),
                        Tag(
                            ids = listOf("1308"),
                            name = "Medical",
                        ),
                        Tag(
                            ids = listOf("391"),
                            name = "Medicina",
                        ),
                        Tag(
                            ids = listOf("390"),
                            name = "Médico",
                        ),
                        Tag(
                            ids = listOf("223"),
                            name = "Medieval",
                        ),
                        Tag(
                            ids = listOf("216"),
                            name = "Meian",
                        ),
                        Tag(
                            ids = listOf("1110"),
                            name = "Melancolía",
                        ),
                        Tag(
                            ids = listOf("1150"),
                            name = "Mentor y aprendiz",
                        ),
                        Tag(
                            ids = listOf("1071"),
                            name = "Mentor y discípulo",
                        ),
                        Tag(
                            ids = listOf("711"),
                            name = "Mentor y estudiante",
                        ),
                        Tag(
                            ids = listOf("984"),
                            name = "Mercenarios",
                        ),
                        Tag(
                            ids = listOf("1339"),
                            name = "Metaficción",
                        ),
                        Tag(
                            ids = listOf("1242"),
                            name = "Miedo",
                        ),
                        Tag(
                            ids = listOf("31", "253"),
                            name = "MILF",
                        ),
                        Tag(
                            ids = listOf("194", "540", "1226"),
                            name = "Militar",
                        ),
                        Tag(
                            ids = listOf("34"),
                            name = "Misterio",
                        ),
                        Tag(
                            ids = listOf("392"),
                            name = "Mitología",
                        ),
                        Tag(
                            ids = listOf("1053"),
                            name = "Mitología nórdica",
                        ),
                        Tag(
                            ids = listOf("652"),
                            name = "Moda",
                        ),
                        Tag(
                            ids = listOf("340", "350"),
                            name = "Moderno",
                        ),
                        Tag(
                            ids = listOf("1170"),
                            name = "Monjas",
                        ),
                        Tag(
                            ids = listOf("180"),
                            name = "Monstruos",
                        ),
                        Tag(
                            ids = listOf("1317"),
                            name = "Chicas monstruo",
                        ),
                        Tag(
                            ids = listOf("974"),
                            name = "Monstruos y criaturas mágicas",
                        ),
                        Tag(
                            ids = listOf("876"),
                            name = "Montañas",
                        ),
                        Tag(
                            ids = listOf("1146"),
                            name = "Movimiento de independencia",
                        ),
                        Tag(
                            ids = listOf("558"),
                            name = "Muerte",
                        ),
                        Tag(
                            ids = listOf("1144"),
                            name = "Muerte de un personaje",
                        ),
                        Tag(
                            ids = listOf("64"),
                            name = "Mujer casada",
                        ),
                        Tag(
                            ids = listOf("51"),
                            name = "Mujer mayor",
                        ),
                        Tag(
                            ids = listOf("648"),
                            name = "Mujer mayor / hombre menor",
                        ),
                        Tag(
                            ids = listOf("254"),
                            name = "Mujer madura",
                        ),
                        Tag(
                            ids = listOf("259"),
                            name = "Mujer traviesa",
                        ),
                        Tag(
                            ids = listOf("513"),
                            name = "Multiverso",
                        ),
                        Tag(
                            ids = listOf("333", "844"),
                            name = "Mundo alternativo",
                        ),
                        Tag(
                            ids = listOf("1044"),
                            name = "Mundo antiguo",
                        ),
                        Tag(
                            ids = listOf("427"),
                            name = "Mundo contemporáneo",
                        ),
                        Tag(
                            ids = listOf("1039"),
                            name = "Mundo criminal",
                        ),
                        Tag(
                            ids = listOf("336"),
                            name = "Mundo de artes marciales",
                        ),
                        Tag(
                            ids = listOf("449", "1002"),
                            name = "Mundo de cultivo",
                        ),
                        Tag(
                            ids = listOf("316"),
                            name = "Mundo de fantasía",
                        ),
                        Tag(
                            ids = listOf("1018"),
                            name = "Mundo de fantasía oriental",
                        ),
                        Tag(
                            ids = listOf("317"),
                            name = "Mundo de juegos",
                        ),
                        Tag(
                            ids = listOf("542"),
                            name = "Mundo de videojuego",
                        ),
                        Tag(
                            ids = listOf("747"),
                            name = "Mundo del espectáculo",
                        ),
                        Tag(
                            ids = listOf("613"),
                            name = "Mundo en ruinas",
                        ),
                        Tag(
                            ids = listOf("1057"),
                            name = "Mundo espiritual",
                        ),
                        Tag(
                            ids = listOf("1027"),
                            name = "Mundo fantasioso",
                        ),
                        Tag(
                            ids = listOf("353"),
                            name = "Mundo fantástico",
                        ),
                        Tag(
                            ids = listOf("412"),
                            name = "Mundo ficticio",
                        ),
                        Tag(
                            ids = listOf("894"),
                            name = "Mundo medieval",
                        ),
                        Tag(
                            ids = listOf("312"),
                            name = "Mundo moderno",
                        ),
                        Tag(
                            ids = listOf("352"),
                            name = "Mundo murim",
                        ),
                        Tag(
                            ids = listOf("357"),
                            name = "Mundo oriental",
                        ),
                        Tag(
                            ids = listOf("623", "514"),
                            name = "Mundo paralelo",
                        ),
                        Tag(
                            ids = listOf("476"),
                            name = "Mundo postapocalíptico",
                        ),
                        Tag(
                            ids = listOf("710"),
                            name = "Mundo real",
                        ),
                        Tag(
                            ids = listOf("520"),
                            name = "Mundo rural",
                        ),
                        Tag(
                            ids = listOf("1109"),
                            name = "Mundo subterráneo",
                        ),
                        Tag(
                            ids = listOf("1091"),
                            name = "Mundo urbano",
                        ),
                        Tag(
                            ids = listOf("1186"),
                            name = "Mundo urbano fantástico",
                        ),
                        Tag(
                            ids = listOf("568"),
                            name = "Mundo virtual",
                        ),
                        Tag(
                            ids = listOf("171"),
                            name = "Murim",
                        ),
                        Tag(
                            ids = listOf("220", "1244", "1296"),
                            name = "Música",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo N",
                    tags = listOf(
                        Tag(
                            ids = listOf("882"),
                            name = "Natación",
                        ),
                        Tag(
                            ids = listOf("495"),
                            name = "Naturaleza",
                        ),
                        Tag(
                            ids = listOf("802"),
                            name = "Navidad",
                        ),
                        Tag(
                            ids = listOf("311"),
                            name = "Negocios",
                        ),
                        Tag(
                            ids = listOf("1015"),
                            name = "Netorare",
                        ),
                        Tag(
                            ids = listOf("924"),
                            name = "Netori",
                        ),
                        Tag(
                            ids = listOf("777"),
                            name = "Nigromancia",
                        ),
                        Tag(
                            ids = listOf("1019", "778"),
                            name = "Ninjas",
                        ),
                        Tag(
                            ids = listOf("232", "281"),
                            name = "Niños",
                        ),
                        Tag(
                            ids = listOf("946"),
                            name = "No consensuado",
                        ),
                        Tag(
                            ids = listOf("1274"),
                            name = "No muertos",
                        ),
                        Tag(
                            ids = listOf("693"),
                            name = "Nobles",
                        ),
                        Tag(
                            ids = listOf("470"),
                            name = "Nobleza",
                        ),
                        Tag(
                            ids = listOf("870"),
                            name = "Nostalgia",
                        ),
                        Tag(
                            ids = listOf("413"),
                            name = "Novela ligera",
                        ),
                        Tag(
                            ids = listOf("1342"),
                            name = "Novela web",
                        ),
                        Tag(
                            ids = listOf("574"),
                            name = "Noviazgo",
                        ),
                        Tag(
                            ids = listOf("888"),
                            name = "Noviazgo falso",
                        ),
                        Tag(
                            ids = listOf("1353"),
                            name = "Novios",
                        ),
                        Tag(
                            ids = listOf("42"),
                            name = "NTR",
                        ),
                        Tag(
                            ids = listOf("969", "1040"),
                            name = "Nudidad",
                        ),
                        Tag(
                            ids = listOf("209"),
                            name = "Nuevo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo O",
                    tags = listOf(
                        Tag(
                            ids = listOf("996"),
                            name = "Obra derivada",
                        ),
                        Tag(
                            ids = listOf("438"),
                            name = "Obsesión",
                        ),
                        Tag(
                            ids = listOf("849"),
                            name = "Océano",
                        ),
                        Tag(
                            ids = listOf("1013"),
                            name = "Ocultismo",
                        ),
                        Tag(
                            ids = listOf("1295"),
                            name = "Trabajadores de oficina",
                        ),
                        Tag(
                            ids = listOf("1331"),
                            name = "Coloreado oficial",
                        ),
                        Tag(
                            ids = listOf("327"),
                            name = "Oficina",
                        ),
                        Tag(
                            ids = listOf("401"),
                            name = "Oficinistas",
                        ),
                        Tag(
                            ids = listOf("589"),
                            name = "Okinawa",
                        ),
                        Tag(
                            ids = listOf("283"),
                            name = "Omegaverse",
                        ),
                        Tag(
                            ids = listOf("379", "493"),
                            name = "One-shot",
                        ),
                        Tag(
                            ids = listOf("235"),
                            name = "One-shot romance",
                        ),
                        Tag(
                            ids = listOf("519", "663"),
                            name = "Opuestos que se atraen",
                        ),
                        Tag(
                            ids = listOf("919"),
                            name = "Osaka",
                        ),
                        Tag(
                            ids = listOf("1014"),
                            name = "Oscuro",
                        ),
                        Tag(
                            ids = listOf("399"),
                            name = "Otaku",
                        ),
                        Tag(
                            ids = listOf("713"),
                            name = "Otome Game",
                        ),
                        Tag(
                            ids = listOf("339"),
                            name = "Otro mundo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo P",
                    tags = listOf(
                        Tag(
                            ids = listOf("650"),
                            name = "Pacto",
                        ),
                        Tag(
                            ids = listOf("989"),
                            name = "Pacto sobrenatural",
                        ),
                        Tag(
                            ids = listOf("708"),
                            name = "Padre e hija",
                        ),
                        Tag(
                            ids = listOf("1049"),
                            name = "Padre e hijo",
                        ),
                        Tag(
                            ids = listOf("675"),
                            name = "Padre soltero",
                        ),
                        Tag(
                            ids = listOf("573"),
                            name = "Padres e hijos",
                        ),
                        Tag(
                            ids = listOf("332"),
                            name = "Palacio",
                        ),
                        Tag(
                            ids = listOf("845"),
                            name = "Palacio imperial",
                        ),
                        Tag(
                            ids = listOf("649"),
                            name = "Pandillas",
                        ),
                        Tag(
                            ids = listOf("1270", "1273"),
                            name = "Parásitos",
                        ),
                        Tag(
                            ids = listOf("733"),
                            name = "Parca",
                        ),
                        Tag(
                            ids = listOf("745"),
                            name = "Pareja",
                        ),
                        Tag(
                            ids = listOf("43"),
                            name = "Pareja casada",
                        ),
                        Tag(
                            ids = listOf("400"),
                            name = "Pareja de oficina",
                        ),
                        Tag(
                            ids = listOf("1166"),
                            name = "Pareja dispareja",
                        ),
                        Tag(
                            ids = listOf("349"),
                            name = "Pareja establecida",
                        ),
                        Tag(
                            ids = listOf("929"),
                            name = "Pareja heterosexual",
                        ),
                        Tag(
                            ids = listOf("256"),
                            name = "Parodia",
                        ),
                        Tag(
                            ids = listOf("408"),
                            name = "Paternidad",
                        ),
                        Tag(
                            ids = listOf("1193"),
                            name = "Patinaje",
                        ),
                        Tag(
                            ids = listOf("498"),
                            name = "Patinaje sobre hielo",
                        ),
                        Tag(
                            ids = listOf("511"),
                            name = "Pecados",
                        ),
                        Tag(
                            ids = listOf("1291"),
                            name = "Pechos grandes",
                        ),
                        Tag(
                            ids = listOf("273"),
                            name = "Peleas",
                        ),
                        Tag(
                            ids = listOf("1243"),
                            name = "Peleas sin poderes",
                        ),
                        Tag(
                            ids = listOf("922"),
                            name = "Percepción",
                        ),
                        Tag(
                            ids = listOf("959"),
                            name = "Periodo Edo",
                        ),
                        Tag(
                            ids = listOf("1136"),
                            name = "Período Sengoku",
                        ),
                        Tag(
                            ids = listOf("824"),
                            name = "Personajes históricos",
                        ),
                        Tag(
                            ids = listOf("260"),
                            name = "Perversión",
                        ),
                        Tag(
                            ids = listOf("425"),
                            name = "Pesca",
                        ),
                        Tag(
                            ids = listOf("301"),
                            name = "Pilates",
                        ),
                        Tag(
                            ids = listOf("1119"),
                            name = "Pintura",
                        ),
                        Tag(
                            ids = listOf("759"),
                            name = "Piratas",
                        ),
                        Tag(
                            ids = listOf("1180", "994"),
                            name = "Planetas alienígenas",
                        ),
                        Tag(
                            ids = listOf("588"),
                            name = "Playa",
                        ),
                        Tag(
                            ids = listOf("1076"),
                            name = "Poderes",
                        ),
                        Tag(
                            ids = listOf("1016"),
                            name = "Poderes oscuros",
                        ),
                        Tag(
                            ids = listOf("701"),
                            name = "Poderes psíquicos",
                        ),
                        Tag(
                            ids = listOf("961"),
                            name = "Poderes sobrenaturales",
                        ),
                        Tag(
                            ids = listOf("686"),
                            name = "Poliamor",
                        ),
                        Tag(
                            ids = listOf("717", "1315"),
                            name = "Policía",
                        ),
                        Tag(
                            ids = listOf("233", "601"),
                            name = "Policial",
                        ),
                        Tag(
                            ids = listOf("270"),
                            name = "Policías",
                        ),
                        Tag(
                            ids = listOf("781"),
                            name = "Poligamia",
                        ),
                        Tag(
                            ids = listOf("537"),
                            name = "Política",
                        ),
                        Tag(
                            ids = listOf("1152"),
                            name = "Polos opuestos",
                        ),
                        Tag(
                            ids = listOf("1178", "1271", "1278", "1279", "465"),
                            name = "Postapocalíptico",
                        ),
                        Tag(
                            ids = listOf("633"),
                            name = "Posesión",
                        ),
                        Tag(
                            ids = listOf("185"),
                            name = "Posible harén",
                        ),
                        Tag(
                            ids = listOf("603"),
                            name = "Precuela",
                        ),
                        Tag(
                            ids = listOf("639", "947"),
                            name = "Prehistórico",
                        ),
                        Tag(
                            ids = listOf("37"),
                            name = "Primer amor",
                        ),
                        Tag(
                            ids = listOf("666"),
                            name = "Prisión",
                        ),
                        Tag(
                            ids = listOf("1063"),
                            name = "Prisioneros",
                        ),
                        Tag(
                            ids = listOf("878"),
                            name = "Problemas sociales",
                        ),
                        Tag(
                            ids = listOf("873"),
                            name = "Procedimientos médicos",
                        ),
                        Tag(
                            ids = listOf("790"),
                            name = "Profecía",
                        ),
                        Tag(
                            ids = listOf("454"),
                            name = "Profesores",
                        ),
                        Tag(
                            ids = listOf("877"),
                            name = "Profesor",
                        ),
                        Tag(
                            ids = listOf("362", "461"),
                            name = "Profesor y alumno",
                        ),
                        Tag(
                            ids = listOf("1043", "1046", "925"),
                            name = "Profesor y alumna",
                        ),
                        Tag(
                            ids = listOf("484"),
                            name = "Profesor y estudiante",
                        ),
                        Tag(
                            ids = listOf("1137"),
                            name = "Profesor / estudiante",
                        ),
                        Tag(
                            ids = listOf("1177"),
                            name = "Prometidos",
                        ),
                        Tag(
                            ids = listOf("688"),
                            name = "Prostitución",
                        ),
                        Tag(
                            ids = listOf("1045"),
                            name = "Protagonista dominado",
                        ),
                        Tag(
                            ids = listOf("1035"),
                            name = "Protagonista femenina",
                        ),
                        Tag(
                            ids = listOf("435"),
                            name = "Protagonista fuerte",
                        ),
                        Tag(
                            ids = listOf("958"),
                            name = "Protagonista inteligente",
                        ),
                        Tag(
                            ids = listOf("978"),
                            name = "Protagonista mayor",
                        ),
                        Tag(
                            ids = listOf("975"),
                            name = "Protagonista no ético",
                        ),
                        Tag(
                            ids = listOf("582"),
                            name = "Protagonista poderoso",
                        ),
                        Tag(
                            ids = listOf("52", "247", "1276"),
                            name = "Psicológico",
                        ),
                        Tag(
                            ids = listOf("557"),
                            name = "Pueblo",
                        ),
                        Tag(
                            ids = listOf("1111"),
                            name = "Pueblo costero",
                        ),
                        Tag(
                            ids = listOf("905"),
                            name = "Pueblo pequeño",
                        ),
                        Tag(
                            ids = listOf("418"),
                            name = "Pueblo rural",
                        ),
                        Tag(
                            ids = listOf("1346"),
                            name = "PununiScan",
                        ),
                        Tag(
                            ids = listOf("168"),
                            name = "Puto-Amo",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo R",
                    tags = listOf(
                        Tag(
                            ids = listOf("1081"),
                            name = "Rakugo",
                        ),
                        Tag(
                            ids = listOf("59"),
                            name = "Rape",
                        ),
                        Tag(
                            ids = listOf("374"),
                            name = "Realeza",
                        ),
                        Tag(
                            ids = listOf("1127"),
                            name = "Realeza y nobleza",
                        ),
                        Tag(
                            ids = listOf("1214"),
                            name = "Realidad",
                        ),
                        Tag(
                            ids = listOf("66"),
                            name = "Realidad virtual",
                        ),
                        Tag(
                            ids = listOf("1082"),
                            name = "Rebelión",
                        ),
                        Tag(
                            ids = listOf("217"),
                            name = "Recomendado",
                        ),
                        Tag(
                            ids = listOf("794"),
                            name = "Reconciliación",
                        ),
                        Tag(
                            ids = listOf("35", "267", "230"),
                            name = "Recuentos de la vida",
                        ),
                        Tag(
                            ids = listOf("326"),
                            name = "Red de influencers",
                        ),
                        Tag(
                            ids = listOf("576"),
                            name = "Redención",
                        ),
                        Tag(
                            ids = listOf("422"),
                            name = "Redes sociales",
                        ),
                        Tag(
                            ids = listOf("29", "172", "1247"),
                            name = "Reencarnación",
                        ),
                        Tag(
                            ids = listOf("572"),
                            name = "Reencuentro",
                        ),
                        Tag(
                            ids = listOf("725"),
                            name = "Refugio subterráneo",
                        ),
                        Tag(
                            ids = listOf("174", "231"),
                            name = "Regresión",
                        ),
                        Tag(
                            ids = listOf("1020"),
                            name = "Regreso",
                        ),
                        Tag(
                            ids = listOf("956"),
                            name = "Reino",
                        ),
                        Tag(
                            ids = listOf("1190"),
                            name = "Reino fantástico",
                        ),
                        Tag(
                            ids = listOf("825"),
                            name = "Reino Unido",
                        ),
                        Tag(
                            ids = listOf("1001"),
                            name = "Reinvención",
                        ),
                        Tag(
                            ids = listOf("443"),
                            name = "Rejuvenecimiento",
                        ),
                        Tag(
                            ids = listOf("398"),
                            name = "Relación a distancia",
                        ),
                        Tag(
                            ids = listOf("732"),
                            name = "Relación amorosa",
                        ),
                        Tag(
                            ids = listOf("1267"),
                            name = "Relación conyugal",
                        ),
                        Tag(
                            ids = listOf("808"),
                            name = "Relación de pareja",
                        ),
                        Tag(
                            ids = listOf("775"),
                            name = "Relación en el trabajo",
                        ),
                        Tag(
                            ids = listOf("320"),
                            name = "Relación entre chicas",
                        ),
                        Tag(
                            ids = listOf("866"),
                            name = "Relación entre hombres",
                        ),
                        Tag(
                            ids = listOf("1233"),
                            name = "Relación entre personas del mismo sexo",
                        ),
                        Tag(
                            ids = listOf("818"),
                            name = "Relación establecida",
                        ),
                        Tag(
                            ids = listOf("561"),
                            name = "Relación falsa",
                        ),
                        Tag(
                            ids = listOf("290"),
                            name = "Relación familiar",
                        ),
                        Tag(
                            ids = listOf("1156"),
                            name = "Relación fingida",
                        ),
                        Tag(
                            ids = listOf("431"),
                            name = "Relación heterosexual",
                        ),
                        Tag(
                            ids = listOf("758"),
                            name = "Relación homosexual",
                        ),
                        Tag(
                            ids = listOf("1084"),
                            name = "Relación homosexual masculina",
                        ),
                        Tag(
                            ids = listOf("1224"),
                            name = "Relación jefe y subordinada",
                        ),
                        Tag(
                            ids = listOf("911"),
                            name = "Relación jefe-empleado",
                        ),
                        Tag(
                            ids = listOf("1229"),
                            name = "Relación laboral",
                        ),
                        Tag(
                            ids = listOf("757"),
                            name = "Relación lésbica",
                        ),
                        Tag(
                            ids = listOf("1191"),
                            name = "Relación madre e hijo",
                        ),
                        Tag(
                            ids = listOf("1028"),
                            name = "Relación maestro-alumno",
                        ),
                        Tag(
                            ids = listOf("1185"),
                            name = "Relación maestro-estudiante",
                        ),
                        Tag(
                            ids = listOf("1106"),
                            name = "Relación padre-hija",
                        ),
                        Tag(
                            ids = listOf("1349"),
                            name = "Relación por contrato",
                        ),
                        Tag(
                            ids = listOf("669"),
                            name = "Relación prohibida",
                        ),
                        Tag(
                            ids = listOf("890"),
                            name = "Relación romántica",
                        ),
                        Tag(
                            ids = listOf("1151"),
                            name = "Relación sáfica",
                        ),
                        Tag(
                            ids = listOf("41"),
                            name = "Relación secreta",
                        ),
                        Tag(
                            ids = listOf("1009"),
                            name = "Relación sobrenatural",
                        ),
                        Tag(
                            ids = listOf("1164"),
                            name = "Relación tabú",
                        ),
                        Tag(
                            ids = listOf("546"),
                            name = "Relación tóxica",
                        ),
                        Tag(
                            ids = listOf("405"),
                            name = "Relaciones adultas",
                        ),
                        Tag(
                            ids = listOf("644"),
                            name = "Relaciones amorosas",
                        ),
                        Tag(
                            ids = listOf("738"),
                            name = "Relaciones disfuncionales",
                        ),
                        Tag(
                            ids = listOf("1157"),
                            name = "Relaciones entre hombres",
                        ),
                        Tag(
                            ids = listOf("346"),
                            name = "Relaciones familiares",
                        ),
                        Tag(
                            ids = listOf("712"),
                            name = "Relaciones heterosexuales",
                        ),
                        Tag(
                            ids = listOf("655"),
                            name = "Relaciones ilícitas",
                        ),
                        Tag(
                            ids = listOf("428"),
                            name = "Relaciones íntimas",
                        ),
                        Tag(
                            ids = listOf("726"),
                            name = "Relaciones múltiples",
                        ),
                        Tag(
                            ids = listOf("1017"),
                            name = "Relaciones prohibidas",
                        ),
                        Tag(
                            ids = listOf("1042"),
                            name = "Relaciones sexuales",
                        ),
                        Tag(
                            ids = listOf("657"),
                            name = "Relaciones sexuales explícitas",
                        ),
                        Tag(
                            ids = listOf("1085"),
                            name = "Relaciones tóxicas",
                        ),
                        Tag(
                            ids = listOf("967"),
                            name = "Religión",
                        ),
                        Tag(
                            ids = listOf("169"),
                            name = "Renacimiento",
                        ),
                        Tag(
                            ids = listOf("789"),
                            name = "República Checa",
                        ),
                        Tag(
                            ids = listOf("678"),
                            name = "Restaurante",
                        ),
                        Tag(
                            ids = listOf("694"),
                            name = "Resurrección",
                        ),
                        Tag(
                            ids = listOf("210"),
                            name = "Retornado",
                        ),
                        Tag(
                            ids = listOf("181"),
                            name = "Rey Demonio",
                        ),
                        Tag(
                            ids = listOf("524"),
                            name = "Rivales",
                        ),
                        Tag(
                            ids = listOf("661"),
                            name = "Rivales a amantes",
                        ),
                        Tag(
                            ids = listOf("680"),
                            name = "Rivales a amigos",
                        ),
                        Tag(
                            ids = listOf("960"),
                            name = "Rivales de amor",
                        ),
                        Tag(
                            ids = listOf("549"),
                            name = "Rivalidad",
                        ),
                        Tag(
                            ids = listOf("896"),
                            name = "Rivalidad amorosa",
                        ),
                        Tag(
                            ids = listOf("909"),
                            name = "Rivalidad entre hermanos",
                        ),
                        Tag(
                            ids = listOf("535"),
                            name = "Robots",
                        ),
                        Tag(
                            ids = listOf("1011"),
                            name = "Robots gigantes",
                        ),
                        Tag(
                            ids = listOf("27"),
                            name = "Romance",
                        ),
                        Tag(
                            ids = listOf("695"),
                            name = "Romance adolescente",
                        ),
                        Tag(
                            ids = listOf("515"),
                            name = "Romance adulto",
                        ),
                        Tag(
                            ids = listOf("386"),
                            name = "Romance Boys Love",
                        ),
                        Tag(
                            ids = listOf("406"),
                            name = "Romance contemporáneo",
                        ),
                        Tag(
                            ids = listOf("1211"),
                            name = "Romance de clases sociales distintas",
                        ),
                        Tag(
                            ids = listOf("342"),
                            name = "Romance de oficina",
                        ),
                        Tag(
                            ids = listOf("780"),
                            name = "Romance en el trabajo",
                        ),
                        Tag(
                            ids = listOf("986"),
                            name = "Romance en la oficina",
                        ),
                        Tag(
                            ids = listOf("525"),
                            name = "Romance entre chicas",
                        ),
                        Tag(
                            ids = listOf("1120"),
                            name = "Romance entre hombres",
                        ),
                        Tag(
                            ids = listOf("1154"),
                            name = "Romance entre mujeres",
                        ),
                        Tag(
                            ids = listOf("393"),
                            name = "Romance entre protagonistas",
                        ),
                        Tag(
                            ids = listOf("394"),
                            name = "Romance escolar",
                        ),
                        Tag(
                            ids = listOf("836"),
                            name = "Romance harén",
                        ),
                        Tag(
                            ids = listOf("580"),
                            name = "Romance heterosexual",
                        ),
                        Tag(
                            ids = listOf("424"),
                            name = "Romance homosexual",
                        ),
                        Tag(
                            ids = listOf("1093"),
                            name = "Romance homosexual masculino",
                        ),
                        Tag(
                            ids = listOf("227"),
                            name = "Romance Josei",
                        ),
                        Tag(
                            ids = listOf("921"),
                            name = "Romance juvenil",
                        ),
                        Tag(
                            ids = listOf("991"),
                            name = "Romance lento",
                        ),
                        Tag(
                            ids = listOf("355"),
                            name = "Romance lésbico",
                        ),
                        Tag(
                            ids = listOf("375"),
                            name = "Romance principal",
                        ),
                        Tag(
                            ids = listOf("722"),
                            name = "Romance prohibido",
                        ),
                        Tag(
                            ids = listOf("769"),
                            name = "Romance sáfico",
                        ),
                        Tag(
                            ids = listOf("1075"),
                            name = "Romance secreto",
                        ),
                        Tag(
                            ids = listOf("238"),
                            name = "Romance Shoujo",
                        ),
                        Tag(
                            ids = listOf("1187"),
                            name = "Romance simulado",
                        ),
                        Tag(
                            ids = listOf("935"),
                            name = "Romance sobrenatural",
                        ),
                        Tag(
                            ids = listOf("1228"),
                            name = "Romance tabú",
                        ),
                        Tag(
                            ids = listOf("739"),
                            name = "Romance trágico",
                        ),
                        Tag(
                            ids = listOf("1305"),
                            name = "Ruinas",
                        ),
                        Tag(
                            ids = listOf("748"),
                            name = "Ruptura amorosa",
                        ),
                        Tag(
                            ids = listOf("667"),
                            name = "Ruptura de compromiso",
                        ),
                        Tag(
                            ids = listOf("420"),
                            name = "Rural",
                        ),
                        Tag(
                            ids = listOf("1029"),
                            name = "Rusia",
                        ),
                        Tag(
                            ids = listOf("744"),
                            name = "Ruta de la Seda",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo S",
                    tags = listOf(
                        Tag(
                            ids = listOf("980"),
                            name = "Sacerdote",
                        ),
                        Tag(
                            ids = listOf("577"),
                            name = "Salud mental",
                        ),
                        Tag(
                            ids = listOf("684"),
                            name = "Salud y fitness",
                        ),
                        Tag(
                            ids = listOf("228", "416"),
                            name = "Samuráis",
                        ),
                        Tag(
                            ids = listOf("1078"),
                            name = "Santuario sintoísta",
                        ),
                        Tag(
                            ids = listOf("1140"),
                            name = "Sátira",
                        ),
                        Tag(
                            ids = listOf("1167"),
                            name = "Sauna",
                        ),
                        Tag(
                            ids = listOf("244"),
                            name = "Ciencia ficción",
                        ),
                        Tag(
                            ids = listOf("988", "319"),
                            name = "Secretos",
                        ),
                        Tag(
                            ids = listOf("1289"),
                            name = "Secretos familiares",
                        ),
                        Tag(
                            ids = listOf("817"),
                            name = "Secuela",
                        ),
                        Tag(
                            ids = listOf("763"),
                            name = "Seducción",
                        ),
                        Tag(
                            ids = listOf("533"),
                            name = "Segunda oportunidad",
                        ),
                        Tag(
                            ids = listOf("12"),
                            name = "Seinen",
                        ),
                        Tag(
                            ids = listOf("1329"),
                            name = "Auto publicado",
                        ),
                        Tag(
                            ids = listOf("581"),
                            name = "Senpai y Kouhai",
                        ),
                        Tag(
                            ids = listOf("898"),
                            name = "Serialización",
                        ),
                        Tag(
                            ids = listOf("152"),
                            name = "Serpiente x ratoncito",
                        ),
                        Tag(
                            ids = listOf("585"),
                            name = "Seúl",
                        ),
                        Tag(
                            ids = listOf("1089"),
                            name = "Sexo explícito",
                        ),
                        Tag(
                            ids = listOf("859"),
                            name = "Sexualidad",
                        ),
                        Tag(
                            ids = listOf("1007"),
                            name = "Shinigami",
                        ),
                        Tag(
                            ids = listOf("1134"),
                            name = "Shogi",
                        ),
                        Tag(
                            ids = listOf("229"),
                            name = "Shonen",
                        ),
                        Tag(
                            ids = listOf("1302"),
                            name = "Shota",
                        ),
                        Tag(
                            ids = listOf("201"),
                            name = "Shoujo",
                        ),
                        Tag(
                            ids = listOf("330"),
                            name = "Shoujo Ai",
                        ),
                        Tag(
                            ids = listOf("175"),
                            name = "Shounen",
                        ),
                        Tag(
                            ids = listOf("308"),
                            name = "Shounen Ai",
                        ),
                        Tag(
                            ids = listOf("807", "976"),
                            name = "Showbiz",
                        ),
                        Tag(
                            ids = listOf("548"),
                            name = "Siglo XIX",
                        ),
                        Tag(
                            ids = listOf("1261"),
                            name = "Sin censura",
                        ),
                        Tag(
                            ids = listOf("331"),
                            name = "Sirenas",
                        ),
                        Tag(
                            ids = listOf("665", "754"),
                            name = "Sirvientas",
                        ),
                        Tag(
                            ids = listOf("1303"),
                            name = "Sirvientes",
                        ),
                        Tag(
                            ids = listOf("176", "183"),
                            name = "Sistema",
                        ),
                        Tag(
                            ids = listOf("26"),
                            name = "Sistema de niveles",
                        ),
                        Tag(
                            ids = listOf("158"),
                            name = "Slice of Life",
                        ),
                        Tag(
                            ids = listOf("166"),
                            name = "Smut",
                        ),
                        Tag(
                            ids = listOf("36", "212"),
                            name = "Sobrenatural",
                        ),
                        Tag(
                            ids = listOf("1283"),
                            name = "Sobrevivencia",
                        ),
                        Tag(
                            ids = listOf("1070"),
                            name = "Sociedad",
                        ),
                        Tag(
                            ids = listOf("474"),
                            name = "Spin-off",
                        ),
                        Tag(
                            ids = listOf("415"),
                            name = "Steampunk",
                        ),
                        Tag(
                            ids = listOf("315"),
                            name = "Streaming",
                        ),
                        Tag(
                            ids = listOf("651", "831"),
                            name = "Subida de nivel",
                        ),
                        Tag(
                            ids = listOf("832"),
                            name = "Subterráneo",
                        ),
                        Tag(
                            ids = listOf("797"),
                            name = "Sueños",
                        ),
                        Tag(
                            ids = listOf("847"),
                            name = "Sueños lúcidos",
                        ),
                        Tag(
                            ids = listOf("578"),
                            name = "Suicidio",
                        ),
                        Tag(
                            ids = listOf("269", "383"),
                            name = "Superhéroes",
                        ),
                        Tag(
                            ids = listOf("49", "1240", "1322"),
                            name = "Superpoderes",
                        ),
                        Tag(
                            ids = listOf("482"),
                            name = "Superación",
                        ),
                        Tag(
                            ids = listOf("522"),
                            name = "Superación personal",
                        ),
                        Tag(
                            ids = listOf("151", "1225"),
                            name = "Supervivencia",
                        ),
                        Tag(
                            ids = listOf("193", "560"),
                            name = "Suspenso",
                        ),
                        Tag(
                            ids = listOf("1323"),
                            name = "Esgrima",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo T",
                    tags = listOf(
                        Tag(
                            ids = listOf("1163", "1281"),
                            name = "Tabaco",
                        ),
                        Tag(
                            ids = listOf("953"),
                            name = "Teatro",
                        ),
                        Tag(
                            ids = listOf("159"),
                            name = "Telenovela",
                        ),
                        Tag(
                            ids = listOf("627"),
                            name = "Templo",
                        ),
                        Tag(
                            ids = listOf("1168"),
                            name = "Tenis",
                        ),
                        Tag(
                            ids = listOf("889"),
                            name = "Tentación",
                        ),
                        Tag(
                            ids = listOf("910"),
                            name = "Tercera edad",
                        ),
                        Tag(
                            ids = listOf("268"),
                            name = "Terror",
                        ),
                        Tag(
                            ids = listOf("1094"),
                            name = "Terror psicológico",
                        ),
                        Tag(
                            ids = listOf("839"),
                            name = "Terrorismo",
                        ),
                        Tag(
                            ids = listOf("58"),
                            name = "Thriller",
                        ),
                        Tag(
                            ids = listOf("772"),
                            name = "Tierra",
                        ),
                        Tag(
                            ids = listOf("605"),
                            name = "Tierra alternativa",
                        ),
                        Tag(
                            ids = listOf("998"),
                            name = "Tierra postapocalíptica",
                        ),
                        Tag(
                            ids = listOf("370", "466", "1218"),
                            name = "Viaje en el tiempo",
                        ),
                        Tag(
                            ids = listOf("760"),
                            name = "Timidez",
                        ),
                        Tag(
                            ids = listOf("1048"),
                            name = "Tira cómica",
                        ),
                        Tag(
                            ids = listOf("1220"),
                            name = "Tira vertical",
                        ),
                        Tag(
                            ids = listOf("602"),
                            name = "Tokio",
                        ),
                        Tag(
                            ids = listOf("1135"),
                            name = "Tokusatsu",
                        ),
                        Tag(
                            ids = listOf("736"),
                            name = "Tomboy",
                        ),
                        Tag(
                            ids = listOf("1003"),
                            name = "Tomo único",
                        ),
                        Tag(
                            ids = listOf("1324"),
                            name = "Cambios de tono",
                        ),
                        Tag(
                            ids = listOf("614"),
                            name = "Torneo",
                        ),
                        Tag(
                            ids = listOf("378", "983"),
                            name = "Torres",
                        ),
                        Tag(
                            ids = listOf("895"),
                            name = "Tortura",
                        ),
                        Tag(
                            ids = listOf("440"),
                            name = "Tortura psicológica",
                        ),
                        Tag(
                            ids = listOf("280"),
                            name = "Trabajo",
                        ),
                        Tag(
                            ids = listOf("643"),
                            name = "Trabajo a tiempo parcial",
                        ),
                        Tag(
                            ids = listOf("883"),
                            name = "Trabajo de oficina",
                        ),
                        Tag(
                            ids = listOf("354"),
                            name = "Trabajadores de oficina",
                        ),
                        Tag(
                            ids = listOf("954"),
                            name = "Trabajo en equipo",
                        ),
                        Tag(
                            ids = listOf("1068"),
                            name = "Trabajo sexual",
                        ),
                        Tag(
                            ids = listOf("46", "1277"),
                            name = "Tragedia",
                        ),
                        Tag(
                            ids = listOf("195"),
                            name = "Trágico",
                        ),
                        Tag(
                            ids = listOf("566"),
                            name = "Traición",
                        ),
                        Tag(
                            ids = listOf("892"),
                            name = "Transformación",
                        ),
                        Tag(
                            ids = listOf("862"),
                            name = "Transformación corporal",
                        ),
                        Tag(
                            ids = listOf("213"),
                            name = "Transmigración",
                        ),
                        Tag(
                            ids = listOf("265"),
                            name = "Transmigración entre mundos",
                        ),
                        Tag(
                            ids = listOf("653"),
                            name = "Transmisión en vivo",
                        ),
                        Tag(
                            ids = listOf("1209"),
                            name = "Traps",
                        ),
                        Tag(
                            ids = listOf("1024", "1026"),
                            name = "Trastornos alimentarios",
                        ),
                        Tag(
                            ids = listOf("618"),
                            name = "Trauma",
                        ),
                        Tag(
                            ids = listOf("934"),
                            name = "Trauma psicológico",
                        ),
                        Tag(
                            ids = listOf("452"),
                            name = "Travestismo",
                        ),
                        Tag(
                            ids = listOf("1182"),
                            name = "Trenes",
                        ),
                        Tag(
                            ids = listOf("799"),
                            name = "Tres Reinos",
                        ),
                        Tag(
                            ids = listOf("444"),
                            name = "Triángulo amoroso",
                        ),
                        Tag(
                            ids = listOf("536"),
                            name = "Trío",
                        ),
                        Tag(
                            ids = listOf("457"),
                            name = "Tsundere",
                        ),
                        Tag(
                            ids = listOf("1299"),
                            name = "Tutor y estudiante",
                        ),
                        Tag(
                            ids = listOf("951"),
                            name = "Tutoría",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo U",
                    tags = listOf(
                        Tag(
                            ids = listOf("38"),
                            name = "Universidad",
                        ),
                        Tag(
                            ids = listOf("297"),
                            name = "Urbano",
                        ),
                        Tag(
                            ids = listOf("664"),
                            name = "Urbano contemporáneo",
                        ),
                        Tag(
                            ids = listOf("798"),
                            name = "Urbano fantástico",
                        ),
                        Tag(
                            ids = listOf("551"),
                            name = "Vacaciones",
                        ),
                        Tag(
                            ids = listOf("673"),
                            name = "Valhalla",
                        ),
                        Tag(
                            ids = listOf("67", "1321"),
                            name = "Vampiros",
                        ),
                        Tag(
                            ids = listOf("453"),
                            name = "Vecindario",
                        ),
                        Tag(
                            ids = listOf("294"),
                            name = "Vecinos",
                        ),
                        Tag(
                            ids = listOf("50", "1287"),
                            name = "Venganza",
                        ),
                        Tag(
                            ids = listOf("552"),
                            name = "Verano",
                        ),
                        Tag(
                            ids = listOf("612", "642"),
                            name = "Viajes",
                        ),
                        Tag(
                            ids = listOf("813", "594"),
                            name = "Viajes dimensionales",
                        ),
                        Tag(
                            ids = listOf("625"),
                            name = "Viaje interdimensional",
                        ),
                        Tag(
                            ids = listOf("992"),
                            name = "Viajes espaciales",
                        ),
                        Tag(
                            ids = listOf("1341"),
                            name = "Vida adulta",
                        ),
                        Tag(
                            ids = listOf("279", "239"),
                            name = "Vida cotidiana",
                        ),
                        Tag(
                            ids = listOf("157", "188", "204"),
                            name = "Vida escolar",
                        ),
                        Tag(
                            ids = listOf("448"),
                            name = "Vida laboral",
                        ),
                        Tag(
                            ids = listOf("544"),
                            name = "Vida lenta",
                        ),
                        Tag(
                            ids = listOf("1237"),
                            name = "Vida nocturna",
                        ),
                        Tag(
                            ids = listOf("631"),
                            name = "Vida rural",
                        ),
                        Tag(
                            ids = listOf("292"),
                            name = "Vida universitaria",
                        ),
                        Tag(
                            ids = listOf("192", "719", "1255", "1307"),
                            name = "Videojuegos",
                        ),
                        Tag(
                            ids = listOf("1239"),
                            name = "Vikingos",
                        ),
                        Tag(
                            ids = listOf("313", "1337"),
                            name = "Villana",
                        ),
                        Tag(
                            ids = listOf("459", "1061"),
                            name = "Villano",
                        ),
                        Tag(
                            ids = listOf("508"),
                            name = "Villana / villano",
                        ),
                        Tag(
                            ids = listOf("1288"),
                            name = "Villano protagonista",
                        ),
                        Tag(
                            ids = listOf("950"),
                            name = "Violación",
                        ),
                        Tag(
                            ids = listOf("897"),
                            name = "Violación o agresión sexual",
                        ),
                        Tag(
                            ids = listOf("299"),
                            name = "Violencia",
                        ),
                        Tag(
                            ids = listOf("842"),
                            name = "Violencia con armas de fuego",
                        ),
                        Tag(
                            ids = listOf("741"),
                            name = "Violencia explícita",
                        ),
                        Tag(
                            ids = listOf("734"),
                            name = "Violencia extrema",
                        ),
                        Tag(
                            ids = listOf("1087"),
                            name = "Violencia psicológica",
                        ),
                        Tag(
                            ids = listOf("382", "1265"),
                            name = "Violencia sexual",
                        ),
                        Tag(
                            ids = listOf("178"),
                            name = "VIP",
                        ),
                        Tag(
                            ids = listOf("674"),
                            name = "Virus",
                        ),
                        Tag(
                            ids = listOf("837"),
                            name = "Vocaloid",
                        ),
                        Tag(
                            ids = listOf("446"),
                            name = "Voleibol",
                        ),
                        Tag(
                            ids = listOf("543"),
                            name = "Voyeurismo",
                        ),
                        Tag(
                            ids = listOf("915"),
                            name = "VTuber",
                        ),
                    ),
                ),
                GenreGroup(
                    letter = "Grupo W–Z",
                    tags = listOf(
                        Tag(
                            ids = listOf("1280"),
                            name = "Guerra",
                        ),
                        Tag(
                            ids = listOf("274", "345"),
                            name = "Webcomic",
                        ),
                        Tag(
                            ids = listOf("776"),
                            name = "Webmanga",
                        ),
                        Tag(
                            ids = listOf("170"),
                            name = "Webtoon",
                        ),
                        Tag(
                            ids = listOf("901"),
                            name = "Western",
                        ),
                        Tag(
                            ids = listOf("310"),
                            name = "Wuxia",
                        ),
                        Tag(
                            ids = listOf("1036"),
                            name = "Wuxia / Murim",
                        ),
                        Tag(
                            ids = listOf("351"),
                            name = "Xianxia",
                        ),
                        Tag(
                            ids = listOf("979"),
                            name = "Xuanhuan",
                        ),
                        Tag(
                            ids = listOf("616"),
                            name = "Yakuza",
                        ),
                        Tag(
                            ids = listOf("298"),
                            name = "Yandere",
                        ),
                        Tag(
                            ids = listOf("146"),
                            name = "Yaoi",
                        ),
                        Tag(
                            ids = listOf("1065"),
                            name = "Yōkai",
                        ),
                        Tag(
                            ids = listOf("803"),
                            name = "Yonkoma",
                        ),
                        Tag(
                            ids = listOf("221"),
                            name = "Yuri",
                        ),
                        Tag(
                            ids = listOf("381"),
                            name = "Zombis",
                        ),
                    ),
                ),
            ),
        ),
    )
}
