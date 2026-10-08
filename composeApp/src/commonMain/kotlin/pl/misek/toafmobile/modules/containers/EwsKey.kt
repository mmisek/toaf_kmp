package pl.misek.toafmobile.modules.containers

/**
 * Dane pojedynczego kluczyka odczytane z EWS (job SCHL_DATEN_LESEN, cmd 0x65).
 * status: 0 - zablokowany/nieuzyty, 1 - zablokowany/uzyty, 2 - wolny/nieuzyty, 3 - wolny/uzyty.
 */
data class EwsKey(
    val number: Int,
    val id: String,
    val status: Int,
    val selfInitDone: Boolean,
    val selfInit: Boolean
)
