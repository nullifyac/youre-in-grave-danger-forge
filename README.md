# You're in Grave Danger - Forge

This repository contains Forge ports of **You're in Grave Danger (YiGD)** for Minecraft 1.16.5, 1.18.2, 1.19.2, and 1.20.1. YiGD adds configurable graves and death handling for player inventory, experience, and respawn behavior.

Each Minecraft version is a separate Gradle project. There is no shared root build.

## Projects

| Directory | Minecraft | Forge | Java compilation target |
| --- | --- | --- | --- |
| [Youre-in-grave-danger-1.16.5-forge](Youre-in-grave-danger-1.16.5-forge/) | 1.16.5 | 36.2.39 | Java 8 |
| [Youre-in-grave-danger-1.18.2-forge](Youre-in-grave-danger-1.18.2-forge/) | 1.18.2 | 40.2.9 | Java 17 |
| [Youre-in-grave-danger-1.19.2-forge](Youre-in-grave-danger-1.19.2-forge/) | 1.19.2 | 43.2.14 | Java 17 |
| [Youre-in-grave-danger-1.20.1-forge](Youre-in-grave-danger-1.20.1-forge/) | 1.20.1 | 47.3.5 | Java 17 |

Versions and dependency coordinates are defined in each project's `gradle.properties`. Release 2.0.20 uses Minecraft-specific archive names, such as `youre-in-grave-danger-forge-1.20.1-2.0.20.jar`. The 1.18.2, 1.19.2, and 1.20.1 builds configure a Java 17 toolchain; the 1.16.5 build sets Java 8 source and target compatibility.

## Building

For the 1.20.1 project, install JDK 17 and run:

```powershell
cd Youre-in-grave-danger-1.20.1-forge
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew build` from the same directory. Build outputs are written to that project's `build/libs/` directory. Run the wrapper inside another version directory to build that version.

The builds resolve Forge, Cloth Config, and optional integration APIs from the Maven repositories declared in `build.gradle`. Local reference downloads and runtime directories are excluded from Git; the projects' `src/main/resources/` assets and Gradle wrappers are included.

## Grave bug validation

All four ports pass clean builds and packaged Forge initialization. The Minecraft 1.20.1 grave fixes pass 17 GameTests, 19 with Accessories beta47, and Prism death/recovery and process-restart checks. See [the validation guide](TESTING.md) for results, repeatable commands and remaining compatibility checks. Downloaded runtimes and validation output stay in ignored `.local` directories.

## Layout

Each version contains:

- `src/main/java/` - mod code, Forge event handlers, mixins, and integrations.
- `src/main/resources/` - mod metadata, textures, translations, and data files.
- `build.gradle`, `settings.gradle`, and `gradle.properties` - build configuration.
- `gradle/wrapper/`, `gradlew`, and `gradlew.bat` - the Gradle wrapper.
- `README.md` and `CHANGELOG.md` - version documentation.
- `convert.py` - the existing source conversion helper; it rewrites Java sources when run.

## Attribution and license metadata

The version READMEs identify the upstream project as [You're in Grave Danger by B1n-ry](https://github.com/B1n-ry/Youre-in-grave-danger). Each version's `gradle.properties` declares `mod_license=MIT` and `mod_authors=b1n_ry`. The upstream MIT license is retained in [LICENSE](LICENSE), each version project, and the runtime and source JARs.
