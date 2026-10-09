# You're in Grave Danger 2.0.22

### Changes

* Added Minecraft 26.1.2 support on NeoForge.
* Updated grave storage, inventory capture, networking, rendering, configuration screens, and item models for Minecraft 26.1.2.
* Updated optional integration with Curios 15 and Traveler's Backpack 26.1.2.
* Added legacy 1.20.1 and 1.21 grave-data migration, including old player profiles, death messages, positions, and item data.

### Fixes

* Carried forward unclaimed grave protection and persistent recovery backups from the Forge releases.
* Retained configured item and experience drops when graves are removed, including while their owner is offline.
* Fixed oldest-grave cleanup discarding contents before configured drops.
* Required a matching grave UUID before removing a saved block so stale history cannot remove a different grave at the same position.
* Enforced a minimum of one history backup so a zero or negative limit cannot discard a new grave before placement.
* Fixed logout announcements removing unclaimed recovery records.
* Used player UUIDs for recovery records so changes to profile data cannot hide graves.
* Made grave-data loading transactional so a malformed record cannot replace the active history or remove existing graves.
* Preserved Curios cosmetic slots and the selected item's render preference through recovery and inventory merging.
* Updated random-spawn equipment NBT and owner heads for the new component format; migrated known old defaults and consumed configured grave items only after a successful spawn.
* Replaced random-spawn placeholders in a single pass so literal item metadata cannot expand recursively; invalid item indices leave loot available for recovery.
* Applied grave access permissions to inventory previews, keys, compasses, and lock requests.
* Fixed the grave compass GUI creating compasses without consuming a recovery compass.
* Fixed the robbing inventory priority setting and owner filtering for Death Sight.
* Replaced the removed vanilla outline texture with an opaque texture available in Minecraft 26.1.2.

### Notes

* Requires Java 25, NeoForge 26.1.2.114 or a compatible newer 26.1.2 build, and Cloth Config 26.1.154 or newer.
* Accessories and Cosmetic Armor Reworked integration are unavailable on this target.
* Legacy migration retains the original save file and refuses to overwrite an existing unreadable destination.
* Data for unavailable inventory integrations remains in grave backups. Restoring that equipment requires a compatible integration adapter.
* Custom random-spawn NBT uses Minecraft 26.1.2's entity equipment format. Automatic conversion applies to the known old defaults; custom templates are retained.

---
