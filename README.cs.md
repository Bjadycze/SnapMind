*[English version](README.md)*

# SnapMind

Android aplikace pro zachytávání myšlenek, navržená pro lidi s ADHD.

Uděláš kdekoli v telefonu screenshot nebo do aplikace něco nasdílíš a SnapMind ti dá cestu
kratší než tři sekundy, jak k tomu připsat poznámku, dokud tu myšlenku ještě máš. Text
z obrázku přečte až potom, všechno drží v zařízení a věci vrací zpátky připomínkovým
systémem, který je postavený tak, aby neotravoval.

Všechno běží lokálně. Žádné účty, žádná synchronizace do cloudu, žádná síťová komunikace
kromě stažení titulku a náhledu k odkazu, který nasdílíš.

## Proč je postavená takhle

Většina appek na zachytávání selhává stejně: promění se v hromadu nedodělaných položek, ze
které je ti při každém otevření hůř. SnapMind to bere jako hlavní návrhové omezení, ne jako
věc k doladění na konec.

- **Nejvýš jedna notifikace denně.** Ne jedna na položku.
- **Nejvýš tři položky v jednom digestu.** Nejstarší nevyřízené první.
- **Když není co ukázat, neposílá se nic.** Žádné „všechno máš hotové".
- **Položka se objeví nejvýš ve třech digestech**, pak jde do tichého archivu. Nikdy se
  nesmaže a zůstane dohledatelná.
- **Žádné odznaky, série, počty nepřečtených, červené puntíky.** Jsou výslovně zakázané, ne
  jen nepřítomné — viz `spec.md` §7.3.

Jediná odměna v aplikaci se spustí, když něco odškrtneš, trvá zhruba půl sekundy a nenechá po
sobě žádný záznam. Odměňuje akci, nikdy stav — každý zakázaný vzorec z odstavce výše totiž
trestá nepřítomnost.

## Co umí

- **Zachycení screenshotu.** Služba na popředí sleduje MediaStore a pošle notifikaci s polem
  pro přímou odpověď, takže poznámku napíšeš ze zamčené obrazovky.
- **Zachycení přes Sdílet.** Obrázky i text z jakékoli aplikace.
- **Obohacení odkazů.** Nasdílený odkaz dostane titulek a náhled, takže karta je poznat
  i za týden.
- **OCR v zařízení.** ML Kit, mimo cestu zachycení — nikdy na něj nečekáš.
- **Klasifikace v zařízení.** Regulární výrazy na data, částky, telefony a kódy objednávek;
  heuristika z klíčových slov na hrubou kategorii. Bez sítě, bez účtu.
- **Ruční a vlastní kategorie.** Klasifikátor hádá, ty ho opravíš — a tvoje vlastní názvy
  kategorií fungují jako filtry.
- **Vyřízení tahem.** Svislý pruh na okraji karty; tahem doleva položku vyřídíš.
- **Hledání a ohlédnutí.** Napříč poznámkami i textem z OCR, včetně archivu.
- **Šest barevných palet**, každá ve světlé i tmavé variantě.

## Dokumentace

| Soubor | Co v něm je |
|---|---|
| `spec.md` | Úplná specifikace, pořadí implementace a každé rozhodnutí i s důvodem |
| `CLAUDE.md` | Dohoda o práci s AI a zamčený řetěz verzí závislostí |
| `docs/spike-results.md` | Naměřené hodnoty z hardwarového spiku (Task 0) |

`spec.md` je autoritativní. Nezaznamenává jen to, co aplikace dělá, ale i proč byla každá
alternativa zamítnutá — většinou proto, že se změřila a neobstála.

## Stav

Verze 1.1, schéma databáze v4. Tasky 1 až 8 jsou hotové. Aktuálně se připravuje první vydání
v Google Play.

Placené funkce v tomhle buildu nejsou: kód pro Play Billing je zapojený a běží, ale jeho UI je
skryté za build flagem a není co koupit. Rozhodnutí, jestli placená větev někdy vyjde, padne
**31. 3. 2027**.

## Build

Potřebuje JDK 21 a Android SDK 36.

Repozitář zatím nemá Gradle wrapper, takže se staví z Android Studia:
**Build → Assemble Project**.

| Komponenta | Verze |
|---|---|
| Android Gradle Plugin | 8.9.3 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| Hilt | 2.58 |
| Room | 2.7.2 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

**Tyhle verze jsou jeden zamčený řetěz.** Posunout jednu bez ostatních rozbije build —
zdůvodnění u každé je v `CLAUDE.md` v sekci *Build environment*. Hilt konkrétně nejde na 2.59
a výš, dokud se nepřejde na AGP 9.

Build s viditelným billing UI: `-Psnapmind.billingUi=true`.

## Poznámky k zařízení

Vyvíjeno a testováno na Honoru 90 Lite s MagicOS 9.0 (Android 15).

Hlavním protivníkem téhle aplikace je agresivní správa baterie od výrobce, a selhává tiše:
volání projde normálně a nestane se nic. Tři důsledky tvarovaly celou architekturu.

- **Žádné plovoucí okno.** `WindowManager.addView` ze služby na pozadí na MagicOS nevykreslí
  nic a nevyhodí výjimku, takže funkce byla vyříznutá — místo aby se dodala jako přepínač,
  který tiše nedělá nic.
- **Žádné časovače ve službách.** Doze odložil `delay()` až o tři hodiny a WorkManager
  o necelé dvě. Veškeré plánování jde přes `AlarmManager`.
- **Onboarding musí uživatele provést systémovým nastavením** — výjimkou z optimalizace
  baterie, autostartem a vypnutím hibernace. Bez toho detekce po prvním restartu přestane
  fungovat.

## Architektura

Kotlin, Jetpack Compose, jedna Activity. Clean Architecture s MVVM a use casy, Hilt na DI,
Room na úložiště, Coroutines a Flow. Všude KSP; kapt se nepoužívá.
