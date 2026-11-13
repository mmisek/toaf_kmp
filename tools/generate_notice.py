# -*- coding: utf-8 -*-
"""Generuje plik NOTICE na podstawie metadanych AboutLibraries.

Zrodlo danych: composeApp/src/commonMain/composeResources/files/aboutlibraries.json
(wytwarzany przez task `exportLibraryDefinitions` pluginu AboutLibraries).

Teksty licencji brane sa w pierwszej kolejnosci z pola `licenseContent` w tym pliku
(AboutLibraries pobiera je z spdx.org, o ile `offlineMode = false`), a gdy go brak -
z plikow zapasowych w tools/notice/<spdxId>.txt.

Uruchomienie:
    gradlew :composeApp:generateNotice
albo bezposrednio:
    python tools/generate_notice.py [--root <katalog projektu>] [--check]

--check nie zapisuje pliku, tylko konczy sie kodem 1, gdy NOTICE jest nieaktualny.
"""
import argparse
import json
import os
import sys

COPYRIGHT_HOLDER = "Marcin Misek"
COPYRIGHT_YEARS = "2026"
PRODUCT = "TOAF / TOAF Mobile"

JSON_PATH = "composeApp/src/commonMain/composeResources/files/aboutlibraries.json"
NOTICE_PATH = "NOTICE"
TEXT_DIR = "tools/notice"

# Licencje bez odpowiednika w SPDX rozpoznajemy po nazwie z metadanych.
LGPL_NAMES = ("lesser gpl", "lgpl")
ASDKL_ID = "ASDKL"

RULE = "=" * 78
THIN = "-" * 78


def read_text(root, name):
    path = os.path.join(root, TEXT_DIR, name)
    if not os.path.isfile(path):
        return None
    return open(path, encoding="utf-8").read().strip()


def entry_of(lib):
    return (
        lib.get("uniqueId") or "",
        lib.get("artifactVersion") or "",
        lib.get("name") or "",
        lib.get("website") or (lib.get("scm") or {}).get("url") or "",
        ", ".join(d.get("name", "") for d in (lib.get("developers") or []))
        or (lib.get("organization") or {}).get("name", ""),
    )


def table(entries):
    lines = []
    for uid, version, name, url, who in entries:
        lines.append("  %s:%s" % (uid, version))
        detail = "      %s" % (name or uid)
        if who:
            detail += "  --  %s" % who
        lines.append(detail)
        if url:
            lines.append("      %s" % url)
    return "\n".join(lines)


def build(root):
    data = json.load(open(os.path.join(root, JSON_PATH), encoding="utf-8"))
    libs = data["libraries"]
    license_meta = data.get("licenses", {})

    lgpl_ids = {
        key for key, value in license_meta.items()
        if any(n in (value.get("name") or "").lower() for n in LGPL_NAMES)
    }

    apache, mit, asdkl, dual, unknown = [], [], [], [], []
    for lib in libs:
        ids = set(lib.get("licenses") or [])
        entry = entry_of(lib)
        if ids & lgpl_ids:
            dual.append(entry)
        elif "Apache-2.0" in ids:
            apache.append(entry)
        elif "MIT" in ids:
            mit.append(entry)
        elif ASDKL_ID in ids:
            asdkl.append(entry)
        else:
            unknown.append((entry, sorted(ids)))

    if unknown:
        for entry, ids in unknown:
            sys.stderr.write("NIEZNANA LICENCJA: %s -> %s\n" % (entry[0], ids))
        raise SystemExit(
            "Przerwano: %d artefakt(y) z licencja spoza obslugiwanych grup.\n"
            "Uzupelnij generate_notice.py zanim wypuscisz NOTICE." % len(unknown))

    for group in (apache, mit, asdkl, dual):
        group.sort()

    # Tresc licencji: najpierw z metadanych (plugin pobiera je z spdx.org do pola `content`),
    # a gdy build szedl offline i pole jest puste - z plikow zapasowych w tools/notice.
    def license_text(spdx_id):
        meta = license_meta.get(spdx_id) or {}
        content = (meta.get("content") or "").strip()
        if content:
            return content, "aboutlibraries.json"
        fallback = read_text(root, "%s.txt" % spdx_id)
        if fallback:
            return fallback, "%s/%s.txt" % (TEXT_DIR, spdx_id)
        raise SystemExit("Brak tresci licencji %s - dodaj %s/%s.txt" % (spdx_id, TEXT_DIR, spdx_id))

    apache_text, apache_src = license_text("Apache-2.0")
    mit_text, mit_src = license_text("MIT")

    # Noty poszczegolnych komponentow (pliki component-<group>_<artifact>.txt).
    component_notices = []
    text_dir = os.path.join(root, TEXT_DIR)
    if os.path.isdir(text_dir):
        for name in sorted(os.listdir(text_dir)):
            if not name.startswith("component-") or not name.endswith(".txt"):
                continue
            uid = name[len("component-"):-len(".txt")].replace("_", ":", 1)
            component_notices.append((uid, read_text(root, name)))

    notices_block = ("\n\n%s\n\n" % THIN).join(
        "%s\n\n%s" % (uid, text) for uid, text in component_notices)

    doc = TEMPLATE.format(
        product=PRODUCT,
        years=COPYRIGHT_YEARS,
        holder=COPYRIGHT_HOLDER,
        rule=RULE,
        thin=THIN,
        n_apache=len(apache),
        n_mit=len(mit),
        n_asdkl=len(asdkl),
        n_dual=len(dual),
        n_total=len(libs),
        apache_list=table(apache),
        dual_list=table(dual),
        mit_list=table(mit),
        asdkl_list=table(asdkl),
        apache_text=apache_text,
        mit_text=mit_text,
        component_notices=notices_block,
    )
    stats = "apache=%d mit=%d asdkl=%d dual=%d suma=%d (Apache z %s, MIT z %s)" % (
        len(apache), len(mit), len(asdkl), len(dual), len(libs), apache_src, mit_src)
    return doc, stats


TEMPLATE = """{product}
Copyright (c) {years} {holder}
All rights reserved.

This product bundles third-party software. The components are listed below,
grouped by the licence they are distributed under, followed by the full text
of each licence.

This file is generated by tools/generate_notice.py from the metadata produced
by the AboutLibraries Gradle plugin. Do not edit it by hand - run
`gradlew :composeApp:generateNotice` instead.

{rule}
1. SUMMARY
{rule}

  Apache License 2.0 ................................. {n_apache} components
  MIT License ........................................ {n_mit} components
  Android Software Development Kit License ........... {n_asdkl} components
  Apache License 2.0 / GNU LGPL v3 (dual-licensed) ... {n_dual} component

  Total: {n_total} components.

  None of the bundled components is distributed under the GPL or the AGPL,
  and no component imposes an obligation to publish the source code of this
  application.

  None of the bundled artifacts ships a META-INF/NOTICE file of its own, so
  there are no upstream notices that have to be reproduced here beyond the
  attributions given below.

{rule}
2. LICENCE ELECTION
{rule}

  com.fazecast:jSerialComm is dual-licensed by its author under the Apache
  License 2.0 and the GNU Lesser General Public License v3. As permitted by
  that dual licence, this product uses jSerialComm under the terms of the
  Apache License 2.0. No LGPL obligation therefore applies to this product.

{rule}
3. COMPONENTS UNDER THE APACHE LICENSE 2.0
{rule}

  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use these files except in compliance with the License. A copy of the License
  is reproduced in section 7.1 below and is also available at
  http://www.apache.org/licenses/LICENSE-2.0

{apache_list}

{thin}
  Dual-licensed, used under the Apache License 2.0 (see section 2):

{dual_list}

{rule}
4. COMPONENTS UNDER THE MIT LICENSE
{rule}

  Permission notice reproduced in section 7.2 below. Where a component ships
  its own copyright notice, that notice is reproduced in section 7.3.

{mit_list}

{rule}
5. COMPONENTS UNDER THE ANDROID SOFTWARE DEVELOPMENT KIT LICENSE
{rule}

  These components are proprietary software of Google LLC, used under the
  Android Software Development Kit License Agreement:
  https://developer.android.com/studio/terms

  They are redistributed only as part of this application, as permitted by
  that agreement. They may not be extracted, redistributed separately,
  modified or reverse engineered.

{asdkl_list}

{rule}
6. JAVA RUNTIME (DESKTOP DISTRIBUTION)
{rule}

  The desktop installers bundle a Java runtime produced by jpackage. When that
  runtime is an OpenJDK build, it is licensed under the GNU General Public
  License, version 2, with the Classpath Exception. That exception expressly
  permits linking independent modules with the runtime without the resulting
  work being covered by the GPL, so no source obligation arises for this
  application. If the build toolchain is changed to a runtime distributed
  under different terms, this section must be revisited.

{rule}
7. LICENCE TEXTS
{rule}

{thin}
7.1 Apache License 2.0
{thin}

{apache_text}

{thin}
7.2 MIT License
{thin}

{mit_text}

{thin}
7.3 Component-specific notices
{thin}

{component_notices}
"""


def main():
    parser = argparse.ArgumentParser(description="Generuje plik NOTICE.")
    parser.add_argument("--root", default=None, help="katalog glowny projektu")
    parser.add_argument("--check", action="store_true",
                        help="nie zapisuj, tylko sprawdz czy NOTICE jest aktualny")
    args = parser.parse_args()

    root = args.root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    doc, stats = build(root)
    target = os.path.join(root, NOTICE_PATH)

    current = open(target, encoding="utf-8").read() if os.path.isfile(target) else None
    if args.check:
        if current == doc:
            sys.stdout.write("NOTICE aktualny - %s\n" % stats)
            return 0
        sys.stderr.write("NOTICE nieaktualny. Uruchom: gradlew :composeApp:generateNotice\n")
        return 1

    if current == doc:
        sys.stdout.write("NOTICE bez zmian - %s\n" % stats)
        return 0
    open(target, "w", encoding="utf-8", newline="\n").write(doc)
    sys.stdout.write("Zapisano %s (%d linii) - %s\n" % (target, len(doc.splitlines()), stats))
    return 0


if __name__ == "__main__":
    sys.exit(main())
