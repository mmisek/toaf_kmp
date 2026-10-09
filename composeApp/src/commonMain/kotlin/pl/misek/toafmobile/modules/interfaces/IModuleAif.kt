package pl.misek.toafmobile.modules.interfaces

/**
 * Odczyt/zapis rekordu AIF (Auftrags-Identifikations-Feld - dane serwisowe/produkcyjne).
 * Odpowiada jobom AIF_LESEN (0x1A, rekord 0x80) oraz AIF_SCHREIBEN (0x3B, rekord 0x80) w SGBD.
 * Dane przekazywane/zwracane jako surowe bajty rekordu (bez nagłówka i sumy kontrolnej).
 */
interface IModuleAif {

    suspend fun readAif(): ByteArray?
    suspend fun writeAif(data: ByteArray): Boolean
}
