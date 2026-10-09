# Grave regression validation

The Minecraft 1.20.1 grave fixes pass the regression suite and Prism death, recovery and process-restart checks. All four ports build successfully. Release 2.0.20 packages these fixes with Minecraft-specific filenames and the upstream MIT license. This guide records verification results and commands for repeating them.

## Minecraft 26.1.2 NeoForge validation

Release 2.0.22 uses Java 25, NeoForge 26.1.2.114, and Cloth Config 26.1.154. These are separate native runtime tests for the new target; the older Forge results below remain specific to their Minecraft versions.

| Runtime profile | Required tests | Result | Evidence |
| --- | --- | --- | --- |
| No optional inventory mod | 41 | All passed | `.local/port-26.1.2/build-final-clean.log` |
| Curios 15.0.0+26.1.2 | 44 | All passed | `.local/port-26.1.2/gametest-curios-final.log` |
| Traveler's Backpack 26.1.2-11.2.8 | 43 | All passed | `.local/port-26.1.2/gametest-travelers-final.log` |

Each total includes one vanilla empty test. YiGD contributes 40 base tests, three additional Curios tests, or two additional Traveler's Backpack tests. The fixtures use real Minecraft objects and native test environments, restore configuration and manager state between cases, and reject ordinary servers before installing test state. Fixtures are excluded from the runtime and sources JARs.

The tested behavior includes:

- Dragon and wither block-removal routines with breakable controls, loaded immunity tags, and the NeoForge entity-destruction hook for linked unclaimed graves.
- Forced block replacement, offline owners, repeated removal, refused mining, same-UUID recreation and relocation, stale history pointing at a different grave, and single item/XP recovery without duplicate drops.
- Enabled and disabled void generation, configured history eviction, a zero history limit, unavailable dimensions, and recovery from retained destroyed-grave backups.
- Actual player death, respawn and recovery; 43 vanilla inventory slots, including body and saddle equipment; soulbinding and item death rules.
- Compressed NBT save/reload and block-entity relinking; UUID identity despite profile changes; malformed-load rollback; legacy 1.20.1 and 1.21 profile, message, position and item migration.
- Native request handling for grave previews, locking, keys and compasses, including foreign-owner permissions and recovery-compass consumption.
- Random-spawn owner heads, successful item consumption with exact metadata, literal placeholders in inserted metadata, oversized indices, and failed spawns preserving pending loot and XP.
- Curios normal and cosmetic items with render preferences, Traveler's Backpack data, and retention of serialized data for an unavailable integration.

Packaged runtime verification on 2026-10-09 used isolated worlds and a new Prism profile with Java 25, NeoForge 26.1.2.114 and Cloth Config 26.1.154:

| Check | Result | Evidence |
| --- | --- | --- |
| Production dedicated-server startup, with YiGD and Cloth Config only | Reached `Done`, saved and stopped normally | `.local/port-26.1.2/packaged-20261009/production-server/production-startup.log` |
| Headless persistence across two Minecraft processes | Namespaced SavedData and physical grave block entity survived | `.local/port-26.1.2/packaged-20261009/persistence/server-result-read.txt` |
| Prism actual death and respawn | Exact named/custom-data stack of 17 diamonds captured once; respawn inventory empty | `.local/port-26.1.2/packaged-20261009/client/client-result-write.txt` |
| Grave rendering and GUI | Front skull, inscription and outline rendered; selection and overview payloads displayed all 17 diamonds | Prism screenshots under the isolated profile |
| Native Cloth Config screen and save | Screen opened and saved; the client priority packet reached the server | Sanitized client logs under `.local/port-26.1.2/packaged-20261009/client/` |
| Full client/server process restart and owner recovery | Same unclaimed grave UUID persisted; exact 17-stack recovered once; grave removed; repeated click did not duplicate items | `.local/port-26.1.2/packaged-20261009/client/client-result-read.txt` |
| Client/server write and read acknowledgements | All four result files report `PASS`; both dedicated-server processes exited 0 | `client/client-result-{write,read}.txt`, `client/server-result-{write,read}.txt`, and `client/exit-{write,read}.json` under the packaged runtime directory |
| Release artifact inspection and reproducibility | Runtime/source JARs passed inspection and regenerated with identical SHA256 values | `.local/port-26.1.2/reproducibility-final.json` |

The final Prism-tested runtime SHA256 is `e9f4b7f915d94ee7d6f4289d23d5ec5022349b486d807e6def4b95e98d7ddeb8`. The packaged release has the same hash. The final build retains the native-tested gameplay classes; later changes normalized equivalent JSON/license text and corrected the client outline texture, which the Prism run verified. Test fixtures were installed only in the isolated runtime profiles and are excluded from release artifacts.

Native boss tests establish the tested vanilla routines and destruction hook; they do not establish every third-party boss's removal behavior. Optional inventory tests use real containers and attachments but do not cover their client equipment screens. The Prism run used no optional inventory integration. The test machine's unavailable sound device did not affect these gameplay and rendering checks.

From the repository root, select an installed JDK 25 and run:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
Push-Location Youre-in-grave-danger-26.1.2-neoforge
try {
    .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 build compileGameTestJava
    .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 runGameTestServer -PcompatProfile=none
    .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 runGameTestServer -PcompatProfile=curios
    .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2' --no-daemon --max-workers=1 runGameTestServer -PcompatProfile=travelers
} finally {
    Pop-Location
}
```

Each profile has its own disposable directory under `build/gameTest/`. The Gradle profiles add the matching optional mod only to the test runtime. Confirm the server reports all required tests passed and Gradle exits successfully; the failed-spawn regression intentionally logs its rejected NBT.

Inspect the packaged release with Python 3.11 or newer, from the repository root:

```powershell
py -3 tools/verify_yigd_neoforge.py Youre-in-grave-danger-26.1.2-neoforge/build/libs/youre-in-grave-danger-neoforge-2.0.22-26.1.2.jar
```

The standard-library checker validates metadata, Java 25 bytecode, JSON and resource paths, immunity tags, item definitions, the grave recipe, license inclusion, and test-fixture exclusion. It does not launch Minecraft.

## Release 2.0.21: native placement checks

All four packaged 2.0.21 JARs passed 12 native placement checks each in fresh dedicated-server worlds, followed by a world save and normal shutdown. These checks exercised the loaded block tags, actual Forge events and `GraveComponent` methods.

| Minecraft | Runtime Forge | Cloth Config | Runtime Java |
| --- | --- | --- | --- |
| 1.16.5 | 36.2.39 | 4.17.101 | 8 |
| 1.18.2 | 40.2.17 | 6.5.102 | 17 |
| 1.19.2 | 43.3.0 | 8.3.134 | 17 |
| 1.20.1 | 47.3.0 | 11.1.118 | 17 |

The checks cover:

- Native soft-tag membership for air, fluids and replaceable plants, with stone, bedrock, chests and existing graves excluded.
- Support-placement events and actual cobblestone support beneath an airborne grave, while preserving existing stone and chest block entities beneath other graves.
- Default configuration and nearby-air selection, plus native generation events that reject occupied block entities.
- Configured graveyard selection that skips an occupied chest, ground scanning through air to a solid surface, and disabled support placement.

The three older ports now define supported vanilla soft blocks explicitly. An optional `minecraft:replaceable` reference permits extensions without requiring a tag absent from those vanilla versions. Minecraft 1.20.1 retains its existing tag and placement behavior. Every production class is unchanged from 2.0.20.

For a quick tag check in a disposable test world, place air or a soft plant and run `execute if block <x> <y> <z> #yigd:replace_soft_whitelist run say soft-tag-match`. Repeat with stone using `execute unless block <x> <y> <z> #yigd:replace_soft_whitelist run say solid-tag-excluded`. For placement regressions, exercise `AllowBlockUnderGraveGenerationEvent`, `GraveGenerationEvent`, `GraveComponent.tryPlaceGrave` and `findGravePos` with the conditions above, restoring configuration between cases.

These native checks had no connected client and do not establish player death, item recovery, grave-history persistence across process restarts or optional-integration gameplay on the older ports. The earlier Minecraft 1.20.1 gameplay results below remain separate evidence.

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
