# Voice Choicer

Aplikacja na Androida do zabawy w dubbingowanie fragmentów filmów z
rodziną/znajomymi: importujesz krótki klip, aplikacja dzieli go na kwestie
poszczególnych postaci, każdy gracz czyta oryginalny tekst i nagrywa swój
głos, a na koniec możesz obejrzeć oryginał i waszą wersję z lektorem obok
siebie.

## Jak to działa

1. **Import** — wybierasz plik wideo z urządzenia. Opcjonalnie dołączasz
   plik napisów `.srt`/`.vtt`:
   - **Z napisami**: aplikacja dzieli klip dokładnie według linii napisów i
     próbuje wykryć postacie po formacie `IMIĘ: tekst` oraz po dialogach z
     myślnikiem (`- Cześć!` / `- Hej!` w jednej linii czasowej).
   - **Bez napisów**: aplikacja dekoduje ścieżkę dźwiękową i wykrywa mowę
     metodą energii sygnału (VAD oparte na ciszy) — wszystkie fragmenty
     trafiają wtedy do jednej "nieznanej" postaci, a tekst do przeczytania
     trzeba wpisać ręcznie. Na urządzeniu nie ma wbudowanego, w pełni
     offline'owego rozpoznawania mowy z pliku, więc to świadomy kompromis
     (patrz "Możliwe rozszerzenia" niżej).
2. **Postacie** — zmieniasz nazwy wykrytych postaci, przypisujesz do nich
   graczy.
3. **Gracze** — dodajesz osoby, które będą nagrywać głosy.
4. **Nagrywanie** — dla każdego fragmentu widzisz oryginalny tekst, możesz
   odtworzyć oryginalny wycinek wideo, a potem nagrać swój głos (można
   nagrywać od nowa dowolną liczbę razy).
5. **Eksport** — aplikacja buduje nowy plik wideo: oryginalny obraz zostaje
   bez zmian (kopiowany bez ponownego kodowania), a ścieżka dźwiękowa to
   wasze nagrania ułożone we właściwych momentach osi czasu (z ciszą tam,
   gdzie nikt jeszcze nie nagrał). Oryginalny plik nigdy nie jest
   nadpisywany, więc możesz porównać obie wersje w zakładkach.

## Struktura projektu

- **`core/`** — czysty moduł Kotlin/JVM (bez zależności od Androida),
  łatwy do przetestowania jednostkowo:
  - `subtitle/SubtitleParser.kt` — parser `.srt`/`.vtt`.
  - `subtitle/SpeakerDetector.kt` — heurystyki wykrywania postaci i dzielenia
    na fragmenty.
  - `audio/SilenceSegmenter.kt` — detekcja mowy na podstawie ciszy (fallback
    bez napisów).
  - `audio/TimelineMixer.kt` — układanie nagranych dźwięków na osi czasu
    filmu (miksowanie z obsługą nakładania się nagrań).
  - `audio/Wav.kt` — kodowanie/dekodowanie WAV PCM16.
  - Testy jednostkowe w `core/src/test` pokrywają wszystkie powyższe.

- **`app/`** — aplikacja na Androida (Kotlin + Jetpack Compose + Material3):
  - `data/db` — baza Room (`Project`, `Character`, `Fragment`, `Player`,
    `Take`).
  - `data/ProjectRepository.kt` — warstwa łącząca DAO z logiką domenową.
  - `importer/ImportPipeline.kt` — łączy `core` z Androidowym I/O
    (kopiowanie pliku, dekodowanie audio, zapis do bazy).
  - `media/` — `AudioDecoder` (MediaExtractor+MediaCodec → PCM),
    `TakeRecorder` (AudioRecord), `DubExporter` (MediaCodec AAC encoder +
    MediaMuxer, kopiowanie ścieżki wideo 1:1).
  - `export/ExportService.kt` — usługa pierwszoplanowa budująca finalny plik
    w tle, z powiadomieniem o postępie.
  - `ui/` — ekrany Compose: lista projektów, import, postacie, gracze,
    nagrywanie, eksport/podgląd.

## Budowanie

To środowisko developerskie (kontener bez Android SDK) nie ma dostępu do
`dl.google.com`/Google Maven, więc **nie da się tu zbudować ani uruchomić
modułu `app`**. Żeby zbudować i uruchomić aplikację:

```bash
# Android Studio (zalecane) — po prostu otwórz folder projektu i kliknij Run,
# Studio samo pobierze SDK/AGP/zależności.

# albo z linii poleceń, mając zainstalowany Android SDK:
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # z podłączonym telefonem/emulatorem
```

Minimalne wymagania: Android Studio Koala+ / Gradle z dostępem do
`google()` i `mavenCentral()`, `minSdk 26`, `compileSdk/targetSdk 35`.

### Moduł `core` — działa od razu, bez Androida

`core` to zwykły moduł Kotlin/JVM, więc jego testy jednostkowe uruchamiają
się nawet w tym kontenerze (i zostały tu faktycznie uruchomione podczas
tworzenia projektu — 19 testów, wszystkie zielone):

```bash
gradle :core:test
```

## Możliwe rozszerzenia

- **Prawdziwe rozpoznawanie mowy offline** — podpięcie biblioteki takiej jak
  Vosk pozwoliłoby automatycznie transkrybować tekst także bez pliku
  napisów (obecnie w tej ścieżce trzeba wpisać tekst ręcznie).
- **Prawdziwa diaryzacja mówców** — obecna detekcja postaci bazuje na
  formacie napisów (prefiks `IMIĘ:` lub dialog z myślnikiem); rozpoznawanie
  głosu po barwie wymagałoby modelu ML i nie działa z samego dźwięku bez
  takiego wsparcia.
- **Synchronizacja długości nagrania z oryginałem** — obecnie nagranie
  gracza jest wklejane od czasu startu oryginalnej kwestii; jeśli gracz
  mówi dłużej niż oryginał, nagranie nakłada się na kolejny fragment
  (miksowane, a nie ucinane) zamiast być automatycznie przyspieszane.
