import os
import xml.etree.ElementTree as ET
import re

SEARCH_PATH = "src\\commonMain\\composeResources\\values"
OUTPUT_FILE = "src\\commonMain\\kotlin\\pl\\misek\\toafmobile\\generated\\ResourceMapper"
# Twój pakiet
PACKAGE_NAME = "pl.misek.toafmobile.generated"

def generate_kotlin_mapper():
    found_resources = []

    # 1. Przeszukaj pliki XML
    if not os.path.exists(SEARCH_PATH):
        print(f"Błąd: Ścieżka {SEARCH_PATH} nie istnieje!")
        return
    modules = []
    for filename in os.listdir(SEARCH_PATH):
        found_resources = []
        if filename.endswith(".xml"):
            file_path = os.path.join(SEARCH_PATH, filename)
            
            match = re.search(r"strings_(.+)\.xml$", filename)
            if not match:
                continue
            raw_name = match.group(1)
            parts = raw_name.split("_")
            pascal = "".join(word.capitalize() for word in parts)
            modules.append(pascal)

            try:
                tree = ET.parse(file_path)
                root = tree.getroot()
                
                for string_tag in root.findall('string'):
                    name = string_tag.get('name')
                    if name and name.startswith(parts[0]):
                        found_resources.append(name)
            except Exception as e:
                print(f"Błąd podczas czytania {filename}: {e}")
        found_resources = sorted(list(set(found_resources)))

        # 2. Generuj plik Kotlin
        outputFileName = OUTPUT_FILE + pascal +".kt"
        os.makedirs(os.path.dirname(outputFileName), exist_ok=True)
        
        with open(outputFileName, "w", encoding="utf-8") as f:
            f.write(f"package {PACKAGE_NAME}\n\n")
            f.write("import org.jetbrains.compose.resources.StringResource\n")
            f.write("import toafmobile.composeapp.generated.resources.*\n\n")
            f.write("object ResourceMapper"+pascal+" {\n")
            f.write("    fun stringResource(resourceName: String): StringResource? {\n")
            f.write("        return when (resourceName) {\n")
            
            for res in found_resources:
                f.write(f'            "{res.lower()}" -> Res.string.{res}\n')
                
            f.write("        else -> {\n")
            f.write("""          println("NO RESOURCE for : $resourceName")\n""")
            f.write("            null\n")
            f.write("        }\n")
            f.write("     }\n")
            f.write("   }\n")
            f.write("}\n")

        print(f"Sukces! Wygenerowano mapowanie dla {len(found_resources)} błędów.")
    for module in modules:
        outputFileName = OUTPUT_FILE +".kt"
        os.makedirs(os.path.dirname(outputFileName), exist_ok=True)
        
        with open(outputFileName, "w", encoding="utf-8") as f:
            f.write(f"package {PACKAGE_NAME}\n\n")
            f.write("import org.jetbrains.compose.resources.StringResource\n")
            f.write("import toafmobile.composeapp.generated.resources.*\n\n")
            f.write("object ResourceMapper {\n")
            f.write("    fun stringResource(resourceName: String): StringResource? {\n")
            f.write("        val prefix = resourceName.split('_')\n")
            f.write("        if (prefix.isEmpty()){\n") 
            f.write("""            println("NO RESOURCE for : $resourceName")\n""")
            f.write("""            return null\n""")
            f.write("""         }\n""")
            f.write("        return when (prefix[0].lowercase()) {\n")
            for module in modules:
                m = module.lower()
                f.write(f'           "{m}" -> ResourceMapper{module}.stringResource(resourceName)\n')
                
            f.write("                else -> {\n")
            f.write("""                  println("NO RESOURCE for : $resourceName")\n""")
            f.write("                    null\n")
            f.write("                }\n")
            f.write("            }\n")
            f.write("       }\n")
            f.write("    }\n")
if __name__ == "__main__":
    generate_kotlin_mapper()