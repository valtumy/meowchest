# Meow Chest (Fabric, Minecraft 26.2)

Kiste öffnen -> Katzen-Miau + 180°-Drehung (Doppelkisten drehen komplett mit).
Rein serverseitig, nutzt den Vanilla-Katzensound.

## Bauen
Voraussetzung: JDK 25.

1. Gradle-Wrapper fehlt in diesem Zip. Entweder `gradlew`, `gradlew.bat` und den
   Ordner `gradle/` aus dem Template von https://fabricmc.net/develop/template
   hierher kopieren, oder mit installiertem Gradle: `gradle wrapper --gradle-version 9.5.1`
2. `./gradlew build`
3. Jar liegt in `build/libs/meowchest-1.0.0.jar` (nicht die `-sources.jar`).
4. Zusammen mit Fabric API (26.2) in den `mods/`-Ordner.

Falls Gradle meckert: loom_version / fabric_api_version / loader_version in
gradle.properties mit dem Template abgleichen.

## Ohne lokales Setup: Jar von GitHub bauen lassen
Projekt in ein neues GitHub-Repo pushen -> Tab "Actions" -> Run "build" ->
unten unter "Artifacts" `meowchest-jar` herunterladen, entpacken, Jar in `mods/`.
