# Voice Choicer

Aplikacja na Androida do zabawy w dubbingowanie fragmentów filmów z
rodziną/znajomymi: importujesz krótki klip, aplikacja dzieli go na kwestie
poszczególnych postaci, każdy gracz czyta oryginalny tekst i nagrywa swój
głos, a na koniec możesz obejrzeć oryginał i waszą wersję z lektorem obok
siebie.

## Jak to działa

1. **Import** — wybierasz plik wideo z urządzenia. Opcjonalnie dodajesz
   napisy `.srt`/`.vtt` (jako plik) albo transkrypcję TurboScribe (jako plik
   `.txt` albo po prostu wklejony tekst — jest osobne pole do wklejenia):
   - **Z napisami .srt/.vtt**: aplikacja dzieli klip dokładnie według linii
     napisów i próbuje wykryć postacie po formacie `IMIĘ: tekst` oraz po
     dialogach z myślnikiem (`- Cześć!` / `- Hej!` w jednej linii czasowej).
   - **Z transkrypcją TurboScribe** (rozpoznawana automatycznie po
     znacznikach czasu w nawiasach, np. `(0:04) Wystarczy.` — czy to z pliku,
     czy wklejona ręcznie): aplikacja rozdziela tekst na kwestie po tych
     znacznikach (koniec każdej kwestii = początek następnej), więc tekst
     jest od razu dokładny — bez błędów offline'owej transkrypcji.
     TurboScribe nie eksportuje jednak informacji o mówcach, więc postacie
     są wykrywane z dźwięku filmu dokładnie tak samo jak w ścieżce bez
     napisów poniżej (prawdziwa diaryzacja ML, z heurystyką wysokości głosu
     jako fallbackiem).
   - **Bez napisów**: aplikacja dekoduje ścieżkę dźwiękową, transkrybuje
     cały fragment offline w jednym przebiegu (model Whisper "base",
     wielojęzyczny, wbudowany w aplikację — działa całkowicie bez
     internetu; sam dzieli dźwięk na kwestie), a następnie przypisuje każdej
     kwestii mówcę przy pomocy prawdziwej diaryzacji ML (sherpa-onnx:
     segmentacja pyannote + embeddingi głosu, klasteryzacja) — a jeśli model
     diaryzacji jest niedostępny, przełącza się na prostszą heurystykę
     wysokości głosu (patrz niżej).
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
  - `subtitle/TurboScribeParser.kt` — parser eksportu transkrypcji TurboScribe
    (tekst ciągły ze znacznikami czasu `(M:SS)`/`(H:MM:SS)` w nawiasach przed
    każdą kwestią, bez bloków jak w .srt/.vtt).
  - `subtitle/SpeakerDetector.kt` — heurystyki wykrywania postaci i dzielenia
    na fragmenty (dla plików .srt/.vtt).
  - `audio/SilenceSegmenter.kt` — detekcja mowy na podstawie ciszy (fallback
    bez napisów).
  - `audio/Resampler.kt` — prosty resampler PCM (ogólnego użytku; obecny
    silnik transkrypcji resampluje sam, ale zostawiony jako przetestowane
    narzędzie do ew. przyszłego użytku).
  - `audio/PitchEstimator.kt` — szacowanie wysokości głosu metodą
    autokorelacji, sygnał wejściowy dla grupowania postaci.
  - `audio/SpeakerClusterer.kt` — grupowanie fragmentów na postacie na
    podstawie wysokości głosu (grupowanie aglomeracyjne z progiem w Hz) —
    heurystyczny fallback, gdy prawdziwa diaryzacja ML jest niedostępna.
  - `audio/SpeakerAligner.kt` — dopasowuje niezależne segmenty diaryzacji
    (kto mówi kiedy) do segmentów transkrypcji (co powiedziano kiedy) po
    zachodzeniu w czasie, bo to dwa oddzielne przebiegi po tym samym
    dźwięku i ich granice się nie pokrywają.
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
    `WhisperTranscriber` (offline rozpoznawanie mowy, model Whisper
    wbudowany w assets), `SherpaDiarizer` (offline diaryzacja mówców przez
    sherpa-onnx), `TakeRecorder` (AudioRecord), `DubExporter`
    (MediaCodec AAC encoder + MediaMuxer, kopiowanie ścieżki wideo 1:1).
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
`google()` i `mavenCentral()`, `minSdk 26`, `compileSdk/targetSdk 35`, oraz
dostęp do internetu przy pierwszym buildzie (pobranie modelu Whisper — patrz
niżej, ~142 MB, cache'owane w `~/.whisper-model-cache` — oraz natywnej
biblioteki i modeli sherpa-onnx do diaryzacji, razem ~55 MB, cache'owane w
`~/.sherpa-onnx-cache`; GitHub, nie Google Maven, więc `dl.google.com`
wystarcza dla samego Gradle/AGP).

### Moduł `core` — działa od razu, bez Androida

`core` to zwykły moduł Kotlin/JVM, więc jego testy jednostkowe uruchamiają
się nawet w tym kontenerze (i zostały tu faktycznie uruchomione podczas
tworzenia projektu — 50 testów, wszystkie zielone):

```bash
gradle :core:test
```

## Rozpoznawanie mowy i postaci bez napisów — jak działa i jakie ma granice

Gdy nie podasz pliku napisów, aplikacja robi wszystko sama, ale w pełni
offline'owy, telefoniczny pipeline ma swoje granice:

- **Transkrypcja (Whisper)**: wielojęzyczny model `ggml-base` (~142 MB,
  MIT, projekt whisper.cpp/OpenAI, poprzez bibliotekę
  `dev.ffmpegkit-maintained:whisper-android`) jest pobierany **przy
  budowaniu aplikacji** (zadanie Gradle `downloadWhisperModel`) i
  wbudowywany w APK — nie jest w repozytorium (zbyt duży plik binarny),
  więc **do zbudowania potrzebny jest dostęp do internetu** (pobiera się
  raz, potem jest cache'owany). Whisper "base" jest wyraźnie dokładniejszy
  niż małe modele oparte na Kaldi (np. Vosk "small", którego używaliśmy
  wcześniej) przy podobnym rozmiarze, kosztem wolniejszej transkrypcji i
  wymogu ABI `arm64-v8a` (biblioteka nie ma prebudowanych binarek dla
  starszych 32-bitowych urządzeń ani emulatorów x86).
- **Grupowanie na postacie (diaryzacja, sherpa-onnx)**: prawdziwy model ML
  — segmentacja mowy modelem pyannote (`sherpa-onnx-pyannote-segmentation-3-0`,
  wersja skwantyzowana int8, ~1.5 MB) + embeddingi głosu modelem WeSpeaker
  (`wespeaker_en_voxceleb_resnet34.onnx`, ~26.5 MB), klasteryzowane przez
  natywną bibliotekę sherpa-onnx (JNI, tylko ABI `arm64-v8a` — brak artefaktu
  Maven dla Androida, więc natywna `.so` i wrapper Kotlin są pobierane/
  dołączane ręcznie, patrz zadania Gradle `downloadSherpaNativeLibs` i
  `downloadDiarizationModels`). Segmenty diaryzacji i segmenty transkrypcji
  Whisper to dwa niezależne przebiegi po tym samym dźwięku, więc
  `SpeakerAligner` łączy je po zachodzeniu w czasie. Jeśli model diaryzacji
  się nie wczyta lub nic nie wykryje, aplikacja przełącza się na prostszą
  heurystykę: `PitchEstimator` szacuje wysokość głosu (autokorelacja), a
  `SpeakerClusterer` grupuje fragmenty o podobnej wysokości głosu (różnica
  < 30 Hz = ta sama postać) — działa gorzej dla dwóch podobnych barwowo
  dorosłych osób tej samej płci. W obu przypadkach zawsze można to
  poprawić ręcznie na ekranie "Postacie" i "Nagrywanie" (zmiana
  przypisania fragmentu do innej postaci).

### Możliwe dalsze rozszerzenia

- **Większy model Whisper** (`small`, ~466 MB) — kosztem rozmiaru APK i
  czasu transkrypcji, dla jeszcze lepszej dokładności.
- **Synchronizacja długości nagrania z oryginałem** — obecnie nagranie
  gracza jest wklejane od czasu startu oryginalnej kwestii; jeśli gracz
  mówi dłużej niż oryginał, nagranie nakłada się na kolejny fragment
  (miksowane, a nie ucinane) zamiast być automatycznie przyspieszane.
