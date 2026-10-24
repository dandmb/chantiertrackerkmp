#!/usr/bin/env bash
# Checks a Play Store bundle before it leaves: signed, right package and version, production API,
# no development tool. Usage: check-release-aab.sh <app.aab> <versionName>
set -u
aab="$1"
expected_version="$2"
failures=0

fail() { echo "ÉCHEC : $1"; failures=$((failures + 1)); }
ok() { echo "ok : $1"; }

manifest() { unzip -p "$aab" base/manifest/AndroidManifest.xml | LC_ALL=C tr -c '[:print:]' '\n'; }
attribute() { manifest | awk -v name="$1" 'found && NF { print; exit } $0 == name { found = 1 }' | grep -o '^[A-Za-z0-9._]*'; }
dex_contains() { unzip -p "$aab" 'base/dex/*.dex' | LC_ALL=C grep -q -a -F "$1"; }

if jarsigner -verify "$aab" 2>&1 | grep -q "jar verified"; then ok "bundle signé"; else fail "bundle non signé"; fi
if [ "$(attribute package)" = "com.dmb.chantiertracker" ]; then ok "paquet com.dmb.chantiertracker"; else fail "paquet attendu com.dmb.chantiertracker"; fi
if [ "$(attribute versionName)" = "$expected_version" ]; then ok "versionName $expected_version"; else fail "versionName attendu $expected_version"; fi
if manifest | grep -q "debuggable"; then fail "build debuggable"; else ok "non debuggable"; fi
if manifest | grep -q "networkSecurityConfig"; then fail "configuration réseau de dev (texte en clair)"; else ok "pas de configuration réseau de dev"; fi
if manifest | grep -q "PreviewActivity"; then fail "outil de dev embarqué : PreviewActivity (compose ui-tooling)"; else ok "pas de PreviewActivity"; fi
if dex_contains "https://api.chantiertracker.com/api/v1"; then ok "API de production présente"; else fail "API de production absente"; fi
if dex_contains "leakcanary"; then fail "outil de dev embarqué : LeakCanary"; else ok "pas de LeakCanary"; fi

exit $failures
