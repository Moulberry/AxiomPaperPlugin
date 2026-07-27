# Axiom Paper Plugin

Serverside component for Axiom

(todo: better readme)

## Download
https://modrinth.com/plugin/axiom-paper-plugin/

## Prism Integration

Axiom Paper Plugin `5.0.4+26.2` supports Prism `4.4` on Paper `26.2`. Prism is optional and is
loaded through Bukkit's service registry, so Axiom continues to work when Prism is absent. Prism
4.4 requires the NBTAPI plugin; the currently verified combination is NBTAPI `2.15.7`.

Enable the records wanted in `plugins/AxiomPaper/config.yml`:

```yaml
prism-logging:
  enabled: true
  records:
    block-changes: true
    biome-changes: true
    entity-spawns: true
    entity-deletes: true
    entity-modifications: true
    player-teleports: false
    player-gamemode-changes: false
    player-fly-speed-changes: false
    player-no-physical-trigger-changes: false
    world-time-changes: false
    world-property-changes: false
    annotation-changes: false
```

The default enabled records are block, biome, and entity changes. Player, world, and annotation
state are disabled by default because they can be high-volume or server-policy specific.

### Recorded Axiom Changes

| Axiom operation | Prism action key | Default |
| --- | --- | --- |
| Direct block placement/removal/replacement, block buffers, and selected tick target | `axiom-place`, `axiom-remove`, `axiom-replace` | enabled |
| Biome buffer changes | `axiom-biome-replace` | enabled |
| Entity spawn/delete | `axiom-entity-spawn`, `axiom-entity-delete` | enabled |
| Entity data, transform, and movement | `axiom-entity-modify` | enabled |
| Entity passenger lists | `axiom-entity-passengers` | enabled with entity modifications |
| Teleport | `axiom-player-teleport` | disabled |
| Game mode | `axiom-player-gamemode` | disabled |
| Fly speed | `axiom-player-fly-speed` | disabled |
| No-physical-trigger state | `axiom-no-physical-trigger` | disabled |
| World time and daylight-cycle rule | `axiom-world-time` | disabled |
| Axiom world properties | `axiom-world-property` | disabled |
| Annotation deltas | `axiom-annotation-change` | disabled |

Each Axiom block record covers the requested block position. If a direct block operation or explicit
tick runs with physics and changes neighboring blocks, those side effects are not guaranteed to have
independent Axiom custom records. Prism's normal listeners may produce additional records, but do not
rely on the Axiom action alone as a complete physics-cascade audit.

Biome buffers are recorded once per changed biome quart (the same 4x4x4 coordinate unit used by
Minecraft's biome container), rather than as one opaque buffer record. This preserves Prism's
position, radius, and world filters, but very large biome edits can intentionally produce many rows.

Prism's normal rollback/restore commands call the custom handlers above. `overwrite` follows Prism
4.4 semantics: without it, operations that already match their destination are skipped; with it,
they are reapplied. Blacklisted blocks and entities are skipped rather than replaced with air.

Prism previews use planning mode and do not mutate these Axiom custom states. Prism 4.4's `/pr undo`
only replays its internal block snapshot type, which is not exposed by `prism-paper-api`; custom
Axiom actions therefore cannot create compatible entries. Use Prism restore to reverse an Axiom
rollback instead of relying on `/pr undo` for Axiom state.

Player actions store both the name and UUID through Prism's `PlayerContainer`, but Axiom uses only
the UUID to find a rollback/restore target. The name remains display and query metadata, matching
Prism's UUID-unique player table and avoiding accidental changes to a different player after a rename.
If a historical record has a UUID but no name, Axiom displays the UUID text and keeps the record
reversible; it never falls back to resolving a target by player name.

### Prism Localization

Prism 4.4 registers English default past tenses when Axiom starts. For a persistent override, add
the keys to Prism's existing locale file, for example
`plugins/prism/locale/messages-zh_CN.properties`, then restart the server. Prism 4.4's locale reload
adds a new Adventure translation source without replacing the custom source registered at startup,
so `/prism configs locales reload` alone is not guaranteed to replace an Axiom default. Axiom does
not ship a separate Prism locale file because locale wording belongs to the server owner.

```properties
prism.past-tense.axiom-place=放置 (Axiom)
prism.past-tense.axiom-remove=移除 (Axiom)
prism.past-tense.axiom-replace=替换 (Axiom)
prism.past-tense.axiom-entity-spawn=生成实体 (Axiom)
prism.past-tense.axiom-entity-delete=删除实体 (Axiom)
prism.past-tense.axiom-entity-modify=修改实体 (Axiom)
prism.past-tense.axiom-entity-passengers=修改乘客 (Axiom)
prism.past-tense.axiom-player-teleport=传送 (Axiom)
prism.past-tense.axiom-player-gamemode=修改游戏模式 (Axiom)
prism.past-tense.axiom-player-fly-speed=修改飞行速度 (Axiom)
prism.past-tense.axiom-no-physical-trigger=修改物理触发 (Axiom)
prism.past-tense.axiom-world-time=修改世界时间 (Axiom)
prism.past-tense.axiom-world-property=修改世界属性 (Axiom)
prism.past-tense.axiom-annotation-change=修改标注 (Axiom)
prism.past-tense.axiom-biome-replace=替换生物群系 (Axiom)
```

Servers with records from earlier PR builds may also add these legacy keys:

```properties
prism.past-tense.axiom-player-no-physical-trigger=修改物理触发 (Axiom)
prism.past-tense.axiom-annotation-snapshot=修改标注 (Axiom)
```

Axiom registers those legacy action keys only so existing records remain readable. New records use
the database-safe `axiom-no-physical-trigger` and delta-based `axiom-annotation-change` keys.

## FAQ

**Axiom works in singleplayer but not when I connect to a multiplayer server running the Axiom Paper Plugin. What gives?**

First, the player must be an op on the server. If the player does not have op permissions, run `/op <playername>`. This player must then disconnect from the server and reconnect.

If you're using an alternative solution for permission management, you must give players the `axiom.default` permission.

If players continue to have issues, they can run the `/whynoaxiom` command for more information.
