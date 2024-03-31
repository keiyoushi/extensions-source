package eu.kanade.tachiyomi.extension.pt.sinensis

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import java.text.SimpleDateFormat
import java.util.Locale

class SinensisScan : PeachScan(
    "Sinensis Scan",
    "https://sinensistoon.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy 'às' HH:mm", Locale("pt", "BR")),
)
