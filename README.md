# You're in Grave Danger for Forge and NeoForge

This repository contains Forge ports of **You're in Grave Danger (YiGD)** for Minecraft 1.16.5, 1.18.2, 1.19.2, and 1.20.1, and a NeoForge port for Minecraft 26.1.2. YiGD adds configurable graves and death handling for player inventory, experience, and respawn behavior.

The [Minecraft 26.1.2 project](Youre-in-grave-danger-26.1.2-neoforge/) uses NeoForge, Java 25, and Cloth Config 26.1.154. Its release version is 2.0.22; the existing Forge projects remain on 2.0.21.

Each Minecraft version is a separate Gradle project. There is no shared root build.

## Projects

| Directory | Minecraft | Loader | Java compilation target |
| --- | --- | --- | --- |
| [Youre-in-grave-danger-1.16.5-forge](Youre-in-grave-danger-1.16.5-forge/) | 1.16.5 | 36.2.39 | Java 8 |
| [Youre-in-grave-danger-1.18.2-forge](Youre-in-grave-danger-1.18.2-forge/) | 1.18.2 | 40.2.9 | Java 17 |
| [Youre-in-grave-danger-1.19.2-forge](Youre-in-grave-danger-1.19.2-forge/) | 1.19.2 | 43.2.14 | Java 17 |
| [Youre-in-grave-danger-1.20.1-forge](Youre-in-grave-danger-1.20.1-forge/) | 1.20.1 | 47.3.5 | Java 17 |
| [Youre-in-grave-danger-26.1.2-neoforge](Youre-in-grave-danger-26.1.2-neoforge/) | 26.1.2 | NeoForge 26.1.2.114 | Java 25 |

Versions and dependency coordinates are defined in each project's `gradle.properties`. Release 2.0.21 uses Minecraft-specific archive names, such as `youre-in-grave-danger-forge-1.20.1-2.0.21.jar`. The 1.18.2, 1.19.2, and 1.20.1 builds configure a Java 17 toolchain; the 1.16.5 build sets Java 8 source and target compatibility.

## Building

For the 1.20.1 project, install JDK 17 and run:

```powershell
cd Youre-in-grave-danger-1.20.1-forge
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew build` from the same directory. Build outputs are written to that project's `build/libs/` directory. Run the wrapper inside another version directory to build that version.

For Minecraft 26.1.2, use JDK 25 and the wrapper inside `Youre-in-grave-danger-26.1.2-neoforge`. The builds resolve their loader, Cloth Config, and optional integration APIs from the Maven repositories declared in `build.gradle`. Local reference downloads and runtime directories are excluded from Git; the projects' `src/main/resources/` assets and Gradle wrappers are included.

## Grave bug validation

All four ports pass clean builds and packaged Forge initialization. The Minecraft 1.20.1 grave fixes pass 17 GameTests, 19 with Accessories beta47, and Prism death/recovery and process-restart checks. See [the validation guide](TESTING.md) for results, repeatable commands and remaining compatibility checks. Downloaded runtimes and validation output stay in ignored `.local` directories.

The NeoForge 26.1.2 tests exercise native grave destruction, death and recovery, equipment, legacy data migration, networking permissions, and optional Curios and Traveler's Backpack inventories. The [26.1.2 validation section](TESTING.md#minecraft-2612-neoforge-validation) records the separate results and commands. Serialized data for an unavailable inventory integration stays in grave backups; restoring those slots requires a compatible integration adapter. This target does not provide Accessories or Cosmetic Armor Reworked adapters.

## Layout

Each version contains:

- `src/main/java/` - mod code, loader event handlers, mixins, and integrations.
- `src/main/resources/` - mod metadata, textures, translations, and data files.
- `build.gradle`, `settings.gradle`, and `gradle.properties` - build configuration.
- `gradle/wrapper/`, `gradlew`, and `gradlew.bat` - the Gradle wrapper.
- `README.md` and `CHANGELOG.md` - version documentation.
- The Forge projects also retain `convert.py`, their existing source conversion helper; it rewrites Java sources when run.

## Attribution and license metadata

The upstream project is [You're in Grave Danger by B1n-ry](https://github.com/B1n-ry/Youre-in-grave-danger). Each project's metadata credits the original author, and the NeoForge 26.1.2 port also credits nullifyac. The upstream MIT license is retained in [LICENSE](LICENSE), each version project, and the runtime and source JARs.
