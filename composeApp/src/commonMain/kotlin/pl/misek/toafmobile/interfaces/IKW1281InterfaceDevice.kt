package pl.misek.toafmobile.interfaces

/**
 * Transmisja bajt po bajcie dla protokolu typu KW1281 (Bosch ABS 5.x, "KWP91/92"):
 * inicjalizacja 5 bodow i potwierdzanie kazdego bajtu negacja. Funkcje blokujace - wolac z watku IO.
 */
interface IKW1281InterfaceDevice {
    /**
     * Inicjalizacja 5 bodow: adres wysylany bit po bicie (LSB pierwszy, 200 ms na bit) przez stan break linii K,
     * potem port ustawiony na [baudrate] 8N1 i wyczyszczony bufor odbiorczy.
     */
    fun kw1281SlowInit(address: Int, baudrate: Int): Boolean

    fun kw1281WriteByte(value: Int): Boolean

    /** Bajt 0..255 albo -1, gdy nic nie nadeszlo w czasie [timeoutMs] */
    fun kw1281ReadByte(timeoutMs: Long): Int

    fun kw1281ClearInput()
}
