# You're in Grave Danger for NeoForge 26.1.2

YiGD 2.0.22 adds configurable graves, inventory and experience recovery, soulbinding, grave compasses, and recovery commands to Minecraft 26.1.2. Install NeoForge 26.1.2.114 or a compatible newer 26.1.2 build, Java 25, and Cloth Config 26.1.154 or newer on both the client and server.

This port uses the [official 1.21 NeoForge source](https://github.com/B1n-ry/Youre-in-grave-danger/tree/1.21-neoforge) and carries the grave protection and recovery fixes from this repository's Forge releases. Existing Forge projects have separate version numbers and build outputs.

## Integrations

Curios 15.0.0+26.1.2 and Traveler's Backpack 26.1.2-11.2.8 are optional. Curios supports normal and cosmetic slots. Accessories and Cosmetic Armor Reworked integration are unavailable for this target because those projects have no compatible 26.x release.

Grave backups retain serialized inventory data when its integration is unavailable. A compatible adapter is required to restore those slots; retaining the data does not make an unsupported integration usable on this target.

Legacy 1.20.1 and 1.21 grave records can migrate to the new namespaced save file. The migration retains the original file and refuses to overwrite an existing unreadable destination. Keep a copy of the whole world before upgrading Minecraft; grave migration does not establish compatibility for other mods' saved data.

Known old random-spawn defaults migrate to the new equipment format. Custom `spawnNbt` templates are retained and must use Minecraft 26.1.2's entity NBT format, including `equipment` for equipped items.

## Build

Use JDK 25 and run the wrapper from this directory:

```powershell
.\gradlew.bat build
```

The runtime JAR is `build/libs/youre-in-grave-danger-neoforge-2.0.22-26.1.2.jar`. The accompanying sources JAR is for development. Install Cloth Config separately; optional integrations are not bundled.

## Validation

Native regression tests and runtime fixtures use a separate `gameTest` source set, which is excluded from the runtime and sources JARs. Run them with:

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat runGameTestServer -PcompatProfile=curios
.\gradlew.bat runGameTestServer -PcompatProfile=travelers
```

See the repository [validation guide](../TESTING.md) for verified results and runtime checks.

## License

Original mod by B1n-ry. NeoForge 26.1.2 port by nullifyac. The upstream [MIT license](LICENSE) is retained in both JARs.
