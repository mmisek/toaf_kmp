package pl.misek.toafmobile.tools

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val tag = "BatteryInfo"

// Start powershell.exe to okolo 0.7-0.9 s na zdrowej maszynie, ale przy zimnym starcie
// i skanowaniu przez antywirusa potrafi byc wyraznie dluzej - stad zapas.
private const val COMMAND_TIMEOUT_SECONDS = 10L

actual suspend fun readBatteryInfo(): BatteryReadResult = withContext(Dispatchers.IO) {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    try {
        when {
            osName.contains("win") -> readWindows()
            osName.contains("mac") || osName.contains("darwin") -> readMacOs()
            osName.contains("nux") || osName.contains("nix") -> readLinux()
            else -> BatteryReadResult.Failed
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$tag [readBatteryInfo], $osName, $ex" }
        BatteryReadResult.Failed
    }
}

/**
 * Jedno uruchomienie powershell.exe zwraca komplet danych:
 *  - Win32_Battery          : poziom naladowania i kod stanu,
 *  - root/wmi BatteryStatus : jednoznaczne PowerOnline/Charging, ale nie na kazdym sprzecie.
 *
 * Wolamy powershell.exe, a nie pwsh - PowerShell 7 instaluje sie osobno, natomiast
 * Windows PowerShell 5.1 jest skladnikiem kazdego Windows 10. Get-CimInstance istnieje
 * od PowerShell 3.0, wiec dziala na wszystkich wspieranych wersjach systemu.
 *
 * Polecenie korzysta wylacznie z cmdletow i potoku, bez konstrukcji jezykowych, dzieki
 * czemu przechodzi tez w ConstrainedLanguage (WDAC/AppLocker). ExecutionPolicy nie ma
 * tu znaczenia, bo dotyczy plikow skryptow, a nie trybu -Command.
 *
 * Uwaga: Win32_Battery nie ma wlasnosci Voltage (jest DesignVoltage) - dopisanie jej
 * do zapytania uniewaznia cale zapytanie, a nie tylko te jedna kolumne.
 */
private fun readWindows(): BatteryReadResult {
    val output = execute(
        listOf(
            powerShellExecutable(), "-NoProfile", "-NonInteractive", "-Command",
            "Get-CimInstance Win32_Battery | Select-Object -First 1 " +
                "-Property EstimatedChargeRemaining,BatteryStatus | Format-List; " +
                "Get-CimInstance -Namespace root/wmi -ClassName BatteryStatus -ErrorAction SilentlyContinue | " +
                "Select-Object -First 1 -Property PowerOnline,Charging | Format-List"
        )
    ) ?: return BatteryReadResult.Failed

    // Format-List pustego wyniku nie drukuje niczego - to komputer bez baterii.
    // Cokolwiek niepustego, czego nie umiemy sparsowac, to juz blad odczytu.
    if (output.isBlank()) return BatteryReadResult.NoBattery

    val level = parseValue(output, "EstimatedChargeRemaining")?.toIntOrNull()
    if (level == null) {
        Logger.e { "$tag [readWindows], nieoczekiwana odpowiedz: $output" }
        return BatteryReadResult.Failed
    }

    val status = parseValue(output, "BatteryStatus")?.toIntOrNull()
    val powerOnline = parseValue(output, "PowerOnline")?.lowercase()?.toBooleanStrictOrNull()
    val charging = parseValue(output, "Charging")?.lowercase()?.toBooleanStrictOrNull()

    // Kody Win32_Battery.BatteryStatus: 1 = praca z baterii, 6-9 = ladowanie,
    // pozostale (2, 3, 11...) oznaczaja zasilanie sieciowe bez ladowania.
    return BatteryReadResult.Ok(
        BatteryInfo(
            level = level,
            isCharging = charging ?: (status in 6..9),
            isPlugged = powerOnline ?: (status != null && status != 1),
        )
    )
}

/**
 * Sciezka z PATH wystarcza na kazdym normalnym systemie, ale PATH bywa okrojony
 * (uruchomienie z uslugi, zepsute srodowisko), wiec najpierw probujemy pelnej sciezki.
 */
private fun powerShellExecutable(): String {
    val systemRoot = System.getenv("SystemRoot") ?: return "powershell.exe"
    val executable = File(systemRoot, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
    return if (executable.canExecute()) executable.absolutePath else "powershell.exe"
}

/**
 * W /sys/class/power_supply katalogi BAT... to baterie, AC... oraz ADP... to zasilacze sieciowe.
 */
private fun readLinux(): BatteryReadResult {
    val supplies = File("/sys/class/power_supply").listFiles() ?: return BatteryReadResult.Failed
    val battery = supplies.firstOrNull { it.name.startsWith("BAT") } ?: return BatteryReadResult.NoBattery

    val level = readSysFile(battery, "capacity")?.toIntOrNull() ?: return BatteryReadResult.Failed
    val status = readSysFile(battery, "status")
    val isCharging = status.equals("Charging", ignoreCase = true)
    val online = supplies
        .filter { it.name.startsWith("AC") || it.name.startsWith("ADP") }
        .any { readSysFile(it, "online") == "1" }

    return BatteryReadResult.Ok(
        BatteryInfo(
            level = level,
            isCharging = isCharging,
            isPlugged = online || isCharging || status.equals("Full", ignoreCase = true),
        )
    )
}

/**
 * pmset -g batt wypisuje zrodlo zasilania i linie baterii, np.:
 *   Now drawing from 'AC Power'
 *    -InternalBattery-0 (id=4653155)  99%; charged; 0:00 remaining present: true
 *
 * Na maszynie bez baterii linia baterii nie wystepuje.
 */
private fun readMacOs(): BatteryReadResult {
    val output = execute(listOf("pmset", "-g", "batt")) ?: return BatteryReadResult.Failed
    if (output.isBlank()) return BatteryReadResult.Failed

    val level = Regex("(\\d{1,3})%").find(output)?.groupValues?.get(1)?.toIntOrNull()
        ?: return BatteryReadResult.NoBattery

    return BatteryReadResult.Ok(
        BatteryInfo(
            level = level,
            isCharging = output.contains("; charging", ignoreCase = true),
            isPlugged = output.contains("AC Power", ignoreCase = true),
        )
    )
}

private fun readSysFile(dir: File, name: String): String? =
    File(dir, name).takeIf { it.canRead() }?.readText()?.trim()?.ifEmpty { null }

/** Zwraca null tylko przy przekroczeniu czasu - pusty wynik to poprawna odpowiedz. */
private fun execute(command: List<String>): String? {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        Logger.w { "$tag [execute], przekroczony czas: ${command.firstOrNull()}" }
        return null
    }
    return output.trim()
}

private fun parseValue(output: String, key: String): String? =
    Regex("(?m)^\\s*$key\\s*:\\s*(\\S+)\\s*$").find(output)?.groupValues?.get(1)
