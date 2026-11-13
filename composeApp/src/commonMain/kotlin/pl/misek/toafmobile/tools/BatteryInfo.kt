package pl.misek.toafmobile.tools

/**
 * Stan baterii urzadzenia odczytany jednorazowo, w momencie wywolania.
 *
 * @param level poziom naladowania 0-100
 * @param isCharging bateria jest w tej chwili ladowana
 * @param isPlugged urzadzenie jest podlaczone do zasilania - moze byc podlaczone i nie ladowac,
 *                  np. przy pelnej baterii albo wlaczonej ochronie ladowania
 */
data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val isPlugged: Boolean,
)

/**
 * Wynik proby odczytu. Rozrozniamy brak baterii od nieudanego odczytu, bo komputer
 * stacjonarny to sytuacja normalna i nie ma o czym informowac uzytkownika alarmem.
 */
sealed interface BatteryReadResult {
    data class Ok(val info: BatteryInfo) : BatteryReadResult

    /** Urzadzenie nie ma baterii - zasilanie wylacznie sieciowe. */
    data object NoBattery : BatteryReadResult

    /** Odczytu nie dalo sie wykonac. */
    data object Failed : BatteryReadResult
}

/**
 * Odczytuje stan baterii.
 *
 * Na desktopie odczyt uruchamia proces systemowy i trwa ulamek sekundy, dlatego
 * wywolujemy go na zadanie, tuz przed operacja, ktora go potrzebuje - nie w petli.
 */
expect suspend fun readBatteryInfo(): BatteryReadResult
