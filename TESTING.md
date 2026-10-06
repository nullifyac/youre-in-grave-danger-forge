# Grave regression validation

The Minecraft 1.20.1 grave fixes pass the regression suite and Prism death, recovery and process-restart checks. All four ports build successfully. Release 2.0.20 packages these fixes with Minecraft-specific filenames and the upstream MIT license. This guide records verification results and commands for repeating them.

## Current evidence

The published Minecraft 1.20.1 YiGD 2.0.19 jar fails ordinary Forge mod initialization with `Registry is already frozen` at `Yigd.java:57`. The isolated reproduction used Java 17, Forge 47.3.5 and Cloth Config 11.1.118. Its original diagnostic target omitted sided mixins; the reusable runner uses a server target so server mixins also load.

The Accessories version comparator accepts the corrected minimum `[1.0.0-beta47,)` for the declared beta47 and beta48 releases, the compiled beta.47 API and stable 1.0. It rejects beta46 and 0.9.0. Beta46 is below the API version used by this port.

The 1.18.2, 1.19.2 and 1.20.1 block and item tag directories use their Minecraft versions' plural paths. Foreign `soulbindable` references are optional in all four ports, so missing newer-Minecraft and integration tags no longer invalidate that tag. Enchantment tags retain their existing paths. The original startup crash and downloaded runtimes are kept in the ignored `.local` directory.

Verification on 2026-10-06 used Java 17 and Forge 47.3.5 for the Minecraft 1.20.1 runtime checks:

| Check | Result | Evidence |
| --- | --- | --- |
| Builds for 1.16.5, 1.18.2, 1.19.2 and 1.20.1 | Passed | `.local/validation/*2026-10-06.log` |
| Packaged metadata, six Accessories version cases and tag paths | Passed | `.local/validation/artifact-candidate-2026-10-06.log` |
| Clean GameTest server | All 17 required tests passed | `.local/validation/gametest-clean-final-2026-10-06.log` |
| Accessories beta47 GameTest server | All 19 required tests passed | `.local/validation/gametest-accessories47-verified-2026-10-06.log` |
| Prism client death and recovery | Exact named stack of 17 diamonds recovered; grave removed | `.local/client-validation-20261006/result.json` |
| Full Minecraft process restart | Same unclaimed grave UUID persisted; exact nine named emeralds recovered once | `.local/client-validation-20261006/restart-result.json` |
| Actual End void death | Default grave at 500, 3, 500; exact seven named gold ingots recovered once | `.local/client-validation-20261006/end-void-result.json` |

The 17 base tests cover loaded protection and item tags, actual dragon and wither block-removal routines with breakable control blocks, entity destruction protection, history eviction, single destruction drops, default retained-backup recovery, claiming, restoration, refused mining, same-tick recreation, relocation, unavailable dimensions, enabled and disabled void generation, and compressed NBT save/reload with block-entity relinking. The final clean run also verifies that optional Accessories test registration works when Accessories is absent.

The two Accessories tests verify occupied normal and cosmetic slots, exact stack NBT/counts, the visibility flag, vanilla inventory and XP through death-rule processing, serialization, clearing and recovery. One claims a real grave. They assign stacks directly to real accessory containers and use detached server players, so they do not cover equip validators or connected-player accessory UI behavior. The older ports have build verification; their gameplay was not exercised in these runs.

The End check used actual void damage in survival with unchanged grave-generation settings. After respawning with an empty inventory, the game reported the grave at the default End minimum Y of 3. A landing platform and creative travel were used only for reaching and claiming it safely. Empty-inventory deaths during travel left that original unclaimed grave intact. Recovery returned the exact named stack, and a second interaction left its count unchanged.

Accessories beta48 logs `InvalidInterfaceMixinException` for its private `ContainerMixin.extendHasAnyMatching` interface injector on stock Forge 47.3.5 and 47.4.10, both using Mixin 0.8.5. Mixin removes the failing mixin and continues, which explains exit zero. Beta47 does not contain that mixin. An isolated diagnostic copy using official Sponge Mixin 0.8.7 successfully injected the handler; it did not change the user's runtime or production dependencies. The checked Forge 47.4.26 installer also declares Mixin 0.8.5, so updating Forge alone does not address this beta48 failure. Beta48 gameplay remains unverified. Both versions also warn about a client renderer accessor on the dedicated-server side.

The stock and diagnostic comparison logs are `.local/validation/startup-accessories47-forge47.4.10-stock.log`, `startup-accessories48-forge47.4.10-stock.log` and `startup-accessories48-forge47.4.10-mixin087-probe.log` in the same directory.

## Release 2.0.20 verification

All four release projects passed clean builds and packaged common mod initialization. The 1.16.5 check used Java 8 and Forge 36.2.39; the other checks used Java 17 with the Forge versions listed in the repository README. These checks stop after mod loading and do not host a server or create a world. Older-port gameplay remains untested.

The release builds use Cloth Config 4.17.101 for 1.16.5, 6.5.102 for 1.18.2, 8.3.134 for 1.19.2, and 11.1.118 for 1.20.1. The 1.16.5 dependency uses Cloth's `cloth-config` mod ID; newer ports use `cloth_config`. Older Traveler's Backpack metadata no longer rejects the releases for those Minecraft versions.

The 2.0.20 GameTest run with Accessories beta47 passed all 19 required tests. Build, packaged startup, artifact inspection and comparison evidence is kept in ignored `.local/release-20261006/`; staged runtime/source JARs and changelogs are in `.local/releases/2.0.20/`.

The final 1.20.1 runtime JAR matches all 227 unchanged gameplay/resource files in the Prism-tested repair candidate. Its only differences are the added license and updated manifest/mod metadata. Artifact inspection also confirmed Java 8 class files for 1.16.5 and Java 17 class files for the other ports.

MixinGradle does not restore its generated refmap when Java compiler output is taken from the Gradle build cache. Compiler caching is disabled for the main source set so a clean build regenerates the refmap. The packaged artifact validator requires a nonempty refmap in addition to metadata and tags. Release JARs exclude GameTests and retain the upstream MIT license.

## Build the Minecraft 1.20.1 candidate

Use JDK 17, including when `JAVA_HOME` currently points to another Java version. From the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
Push-Location Youre-in-grave-danger-1.20.1-forge
try {
    .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 compileGameTestJava build
} finally {
    Pop-Location
}
```

Substitute the installed JDK 17 path on another machine. Build one Minecraft version at a time. Minecraft setup tools can use memory beyond the Gradle heap, so watch available memory during the first build. The MixinGradle plugin is pinned to published version 0.7.38.

The separate `gameTest` source set is excluded from the release jar. The original repair candidates used version 2.0.19; release builds use 2.0.20. The Prism results below refer to those repair candidates, whose production code is retained in the release builds.

## Inspect the packaged candidate

After a successful build, run the artifact validator from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/yigd-triage/Test-YigdArtifact.ps1 `
    -ForgeRuntimePath .local/runtime/forge-1.20.1 `
    -JarPath Youre-in-grave-danger-1.20.1-forge/build/libs/youre-in-grave-danger-forge-1.20.1-2.0.20.jar
```

This checks packaged dependency metadata, the prerelease version range and the relevant block and item tags. It does not launch Minecraft. The execution-policy override applies only to this invocation.

## Check common mod initialization

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/yigd-triage/Invoke-YigdTriage.ps1 `
    -ForgeRuntimePath .local/runtime/forge-1.20.1 `
    -ModJarPath Youre-in-grave-danger-1.20.1-forge/build/libs/youre-in-grave-danger-forge-1.20.1-2.0.20.jar `
    -ClothConfigJarPath .local/runtime/forge-1.20.1/mods/cloth-config-11.1.118-forge.jar `
    -Label candidate
```

The runner gives each invocation a fresh directory, records input hashes, limits the heap to 1 GiB, and calls Forge's mod loader without starting server Main, hosting a server or creating a world. Exit zero confirms common mod initialization; it does not validate client rendering, player death or recovery. Use the downloaded published jar with `-Label baseline` to reproduce the original failure. Additional mods can be supplied explicitly with `-AdditionalModJarPath`.

Accessories Forge beta47 and beta48, plus Cloth Config 11.1.136, are cached in `.local/reference/accessories/`. Test one Accessories version at a time. Beta47 came from the official Wisp Forest Maven; beta48 came from Modrinth. The jars declare versions without the dot after `beta`, although their filenames include it. For example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/yigd-triage/Invoke-YigdTriage.ps1 `
    -ForgeRuntimePath .local/runtime/forge-1.20.1 `
    -ModJarPath Youre-in-grave-danger-1.20.1-forge/build/libs/youre-in-grave-danger-forge-1.20.1-2.0.20.jar `
    -ClothConfigJarPath .local/reference/accessories/cloth-config-11.1.136-forge.jar `
    -AdditionalModJarPath '.local/reference/accessories/accessories-neoforge-1.0.0-beta.47+1.20.1.jar' `
    -Label candidate-accessories47
```

## Prepared Prism instance

The installed launcher has a separate instance named **YIGD 1.20.1 repair test**, ID `YIGD-1_20_1-repair`. It contains the candidate and Cloth Config, selects JDK 17, Forge 47.3.5 and LWJGL 3.3.1, and sets a 4 GiB maximum heap. It passed client and restart tests in the disposable survival world `YIGD-20261006-Smoke`. Test game processes were closed after saving. Screenshots, sanitized logs and the pre-restart grave backup are in `.local/client-validation-20261006/`; corrected import packages and hashes are in `.local/prism/`.

The generator can prepare another import package without installing it:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/yigd-triage/New-YigdPrismInstance.ps1 `
    -ModJarPath Youre-in-grave-danger-1.20.1-forge/build/libs/youre-in-grave-danger-forge-1.20.1-2.0.20.jar `
    -ClothConfigJarPath .local/runtime/forge-1.20.1/mods/cloth-config-11.1.118-forge.jar
```

An explicit `-InstancesDirectory` installs a new instance and refuses an existing target. Open the prepared instance in Prism for client testing. The generator also prints the installed launcher's CLI command for later use.

## Run the isolated GameTests

From the 1.20.1 project, with JDK 17 selected, run:

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 runGameTestServer
```

This run uses the separate `run-gametest` directory and a 2 GiB game heap. The regression fixtures reject ordinary live-world servers before changing any global test state.

The base run executes 17 tests. To include the two Accessories beta47 tests, run from the same project:

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 `
    -I ../tools/yigd-triage/accessories-beta47-gametest.init.gradle runGameTestServer
```

The initialization script adds the optional runtime dependencies for that invocation only. It remaps Accessories and its nested Fabric modules into the development runtime and disables transitive resolution to avoid duplicate launcher modules. Release dependencies are unchanged. Without Accessories, the optional test generator contributes no test functions.

## Gameplay coverage and remaining checks

Use disposable worlds and distinctive named item stacks. Record Minecraft, Forge, YiGD and optional mod versions, the configuration, death coordinates, logs and item counts.

| Scenario | Verified coverage | Remaining check |
| --- | --- | --- |
| Clean client startup and claim | Prism survival death, empty respawn inventory, exact owner claim and grave removal | Older-version client gameplay |
| Accessories | Beta47 occupied normal/cosmetic inventory serialization and real grave claim | Connected-player equip UI; beta48 after its upstream mixin issue is addressed |
| Vanilla bosses | Native dragon wall removal and wither body removal destroy controls while preserving graves | Full fights and navigation |
| Cataclysm | Source protection through Forge's entity-destruction hook | Reproduce a named boss, mod version and attack |
| History eviction | Limit of one removes the old grave and drops its configured contents exactly once | Player-driven long-history scenario |
| External replacement | Drops enabled and default disabled preserve the expected status and recovery data | Broader modpack block-replacement interactions |
| Mining and claiming | Refused mining, successful claim, restore, state change and relocation GameTests | Enabled break-to-claim client flow |
| Void | Both Overworld settings in GameTests; actual survival End void death and exact recovery at default minimum Y=3 | Nondefault placement settings and modded dimensions |
| Restart recovery | Gzip NBT reload and actual Prism process restart preserve an unclaimed grave and exact contents | Destroyed-backup history GUI restoration after restart |

Mods that directly replace blocks can bypass Forge's entity-destruction check. The removal callback should still retain the recovery record and honor destruction settings. A grave whose dimension no longer exists cannot drop items there; its history-limit behavior remains a limitation to review separately.

The existing grave loot-table JSON uses a newer Minecraft schema in a singular `loot_table` directory. It is not activated or migrated by these fixes and needs a separate, version-specific review before changing decorative grave drops.
