# Arexhub (Minecraft 1.21.4 / Fabric)

Oyun içinden Modrinth'teki texture pack'leri ara, indir ve kur.

## Derleme
1. JDK 21 kur.
2. Klasörü IntelliJ IDEA ile aç (Gradle projesi olarak içe aktarır), ya da terminalde:
   `gradle wrapper --gradle-version 8.12` sonra `./gradlew build`
3. `build/libs/arexhub-1.0.0.jar` dosyasını `.minecraft/mods` içine at.
4. Fabric Loader (0.16.10+) ve Fabric API (1.21.4) gerekli.

## Kullanım
- **H** tuşu veya ana menü / duraklatma menüsündeki sağ üst **Arexhub** butonu.
- Ara, **İndir**'e bas; pack `resourcepacks` klasörüne iner ve Texture Packs menüsünde görünür.

## GitHub ile otomatik derleme
Projeyi GitHub'a yükle. Actions sekmesinde "Arexhub Build" çalışır; bitince
en alttaki Artifacts bölümünden `arexhub-jar` dosyasını indir.
