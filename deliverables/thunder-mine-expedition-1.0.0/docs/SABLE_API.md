# Sable 2.0.6: physics, native platforms, and world persistence

This is a read-only investigation of the uploaded `sable-neoforge-1.21.1-2.0.6.jar` and its bundled libraries. Java class signatures/bytecode and archive metadata were inspected; the uploaded JAR was not altered. Static findings do not prove a successful Minecraft launch or ride.

## Required environment

| Component | Actual uploaded artifact requirement |
|---|---|
| Minecraft | Exactly 1.21.1 |
| Loader | NeoForge 21.1.228 or newer |
| Java | Java 21; mixin compatibility level `JAVA_21` |
| Create | Optional to Sable alone; declared supported range `[6.0.10,6.1.0)` |
| Flywheel | Optional to Sable alone; 1.0.6 or newer |
| Sodium | If installed, 0.8.12-alpha.2+mc1.21.1 or newer |
| ScalableLux | Declared incompatible |

These artifacts are not compatible with the preceding Coaster Engineering environment (Minecraft 1.20.1, Forge 47.4.10, Java 17). A separate 1.21.1/NeoForge instance and new world are required.

The uploaded Sable JAR declares the PolyForm Shield License 1.0.0. This project uses the existing artifact unchanged and calls its exposed API; inspection output and extracted third-party binaries are development evidence, not original helper source to redistribute.

## Bundled dependencies

`META-INF/jarjar/metadata.json` includes:

- Sable Companion common 1.21.1 / 1.6.0.
- Sable Rapier pipeline 1.21.1 / 2.0.6.
- Veil NeoForge 1.21.1 / 4.3.2.

Veil contains GLSL Processor 0.2.3 and Molang Compiler 3.1.1.19 as further bundled libraries. Do not install duplicate versions merely because these classes are referenced.

`PhysicsPipelineProvider.INSTANCE` selects providers using Java `ServiceLoader` and maximum load priority. The bundled real Rapier provider has default priority 1000; Sable's static fallback has priority 900. Tests must confirm the runtime selected `RapierPhysicsPipelineProvider`, rather than treating any loaded world as a physics pass.

## Native platforms and Linux cloud compatibility

The nested Rapier JAR stores an LZ4-framed ZIP resource, `/natives/sable_rapier/sable_rapier_binaries.zip.l4z`, containing:

| Native filename | Bytes |
|---|---:|
| `sable_rapier_x86_64_linux.so` | 1,887,608 |
| `sable_rapier_aarch64_linux.so` | 1,594,408 |
| `sable_rapier_x86_64_windows.dll` | 1,658,368 |
| `sable_rapier_aarch64_windows.dll` | 1,340,416 |
| `sable_rapier_x86_64_macos.dylib` | 4,693,160 |
| `sable_rapier_aarch64_macos.dylib` | 4,257,344 |

The Linux x86-64 binary is ELF64 x86-64 and requires glibc symbols through GLIBC_2.17. Its dynamic dependencies are libc, libm, libpthread, libdl, and the normal x86-64 loader. This matches the cloud's Linux x86-64 platform; no static native-platform blocker was found. Actual JNI loading and simulation remain runtime checks.

`Rapier3D` chooses architecture from `os.arch` and operating system from Minecraft's platform enum. It extracts its bundled matching file into `<game directory>/.sable/natives/` and calls `System.load` with the absolute path. The loaded native is generated from the unchanged JAR; it does not require a separate Rapier download or a fabricated native-selection JVM flag.

GPU rendering is separate from server-side JNI physics. Mesa software rendering can be useful for client evidence, but native presence does not prove client renderer correctness or practical FPS.

## Dedicated-server initialization and real physics

`SableNeoForge` registers common/server configuration, commands, network packets, and resource loaders without invoking its client initializer. On a real `ServerLevel`, Sable creates a `ServerSubLevelContainer`, then a `SubLevelPhysicsSystem` and tracking system. This is an intended dedicated-server path; launch still must verify all three supplied MODs together.

Each server tick is 0.05 seconds. `sub_level_substeps_per_tick` defaults to 2 and supports integers 1 through 10; default physical timestep is therefore 0.025 seconds. Changing substeps is a supported configuration adjustment, but initial tests should retain the uploaded MOD defaults.

The pipeline handles world-block colliders, rigid-body mass/inertia, forces, constraints, and actual movement. Existing Simulated Coasters attaches track constraints and chain-lift speed matching to `ForgeSablePrePhysicsTickEvent`; the helper should create native objects and infrastructure, not replace their movement with a new coaster physics engine.

Relevant supported public signatures:

```java
ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
SubLevelPhysicsSystem physics = container.physicsSystem();
RigidBodyHandle body = RigidBodyHandle.of(serverSubLevel);
Vector3d linearVelocity = body.getLinearVelocity(new Vector3d());
Vector3d angularVelocity = body.getAngularVelocity(new Vector3d());
```

Velocity here is blocks/second, not the old Coaster Engineering blocks/tick convention. Measurements should use actual pose displacement/time and may display km/h equivalent as `blocksPerSecond * 3.6`.

`RigidBodyHandle` exposes impulse, angular impulse, velocity increment, and teleport methods. Teleport is suitable for explicit setup/recovery only: a tour moved by repeated helper teleports is not evidence of native coaster running.

For assembly from existing world blocks:

```java
ServerSubLevel assembled = SubLevelAssemblyHelper.assembleBlocks(
    serverLevel, anchor, selectedBlocks, boundingBox);
```

This API moves blocks, block entities, relevant entities and tracking points into an allocated native plot, creates native mass/collision data, and sets its pose from center of mass. For a coaster cart, use the more specific unchanged Simulated Coasters spawner described next.

## Simulated Coasters cart integration

The exact uploaded Simulated Coasters 0.1.5 public factory is:

```java
ServerSubLevel cart = CoasterCartSpawner.spawnMinimalContraption(
    serverLevel, plotOriginWorld, trackOrientation, initialGraphHit, true);
```

There are also three-argument and four-argument overloads. The factory allocates a real Sable sublevel, creates the `simulatedcoasters` cart-bearing block in its embedded plot, updates mass statistics, and invokes the existing initial rail-snap path. It returns `null` when creation is unavailable; the helper must report this as a failed setup, not silently create a decorative substitute.

The minimal factory creates a bearing, not a complete seated mine vehicle. Original helper mine-car body blocks and existing Create seat blocks can be placed through:

```java
EmbeddedPlotLevelAccessor embedded = cart.getPlot().getEmbeddedLevelAccessor();
embedded.setBlock(plotLocalPosition, blockState, 3);
```

If a body extends beyond the already created center chunk, use the real plot chunk allocation API first. Keep exactly one cart-bearing block in each native cart: the existing duplicate enforcer removes invalid duplicates. Changing body blocks alters native mass/colliders and can alter physical behavior; native riding tests must include the finished body and seats.

The unchanged Coaster Cart's engaged native collision shape starts at bearing local Y=0.5; the rail guide constrains its bearing center at approximately track-up +0.3125. These are actual inspected implementation details, not clearance guarantees for arbitrary added bodies. Clearance checks must include the entire seated vehicle geometry.

The existing chain-lift controller reads actual chain-lift spans and kinetic speed. It increases/decreases tangential velocity through the existing Rapier pipeline, with maximum matching rates of 64 or 32 blocks/second² depending on grip state. It is not an automatic motor on every rail. An unsupported incline may stall unless a genuine powered span or sufficient measured kinetic energy exists.

## Persistence: what must be included in the world ZIP

A cart is a moving Sable block plot, not a vanilla minecart entity saved solely under `entities/`. Its files must remain in the world archive.

For the Overworld, persistence includes:

- `sublevels/r.<regionX>.<regionZ>.slvlr`: holding-chunk indexes.
- `sublevels/r.<regionX>.<regionZ>.<storageIndex>.slvls`: actual sublevel data.
- Optional matching `.r/` and `.s/` external-data directories.
- `data/sable_sub_level_occupancy.dat`.
- `data/sable_sub_level_force_load_tickets.dat` where applicable.
- `data/sable_tracking_points.dat` where applicable.
- Normal Minecraft world/block-entity, Create, and Simulated Coasters saved data.

Other dimensions use their own dimension data/sublevel directories. Package the entire stopped world's directory, not just `region/` and `level.dat`.

The native serializer saves sublevel UUID, plot blocks and block entities, quaternion pose, world bounds, display name, loading-dependency UUIDs, and `user_data`. Simulated Coasters train-link records are stored in native cart user data. A runtime ID is transient and must not serve as the world's persistent cart identity.

It additionally stores linear velocity above 0.1 blocks/second and angular velocity above its implementation threshold. Reload applies `sub_level_velocity_retained_on_load`, default 0.9. A delivered playable world should therefore be normally saved while the train is stopped at the station; a moving saved tour can resume with changed energy.

Sable injects `holdingChunkMap.saveAll()` into normal `ServerLevel.save`, before `saveLevelData()`. Normal `save-all flush` / server stop / singleplayer Save and Quit is the correct save path. Its normal close flushes/ closes native storage. Reconstructing only pose or graph NBT offline does not demonstrate native save/load correctness.

Explicit force loading is possible through:

```java
container.addForceLoadTicket(cart,
    SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
```

Tickets are persisted using the native type codec. Use a helper-owned registered ticket type for a maintainable attraction controller if permanent cart loading is required. Tickets keep the native cart available; they do not prove every scenery/track world chunk is loaded. Track-route chunk tickets are a distinct requirement.

## Required runtime evidence

1. The dedicated server logs the selected real Rapier provider and loads all uploaded MODs.
2. The native factory produces a cart with correct bearing/seat/body blocks.
3. Recorded pose changes are native simulation output; no per-tick helper teleport.
4. Cart remains rail-engaged throughout the final course, including hills and curves.
5. The station controller brakes the actual body and leaves it at a real accessible seat.
6. Normal save/stop creates nonempty `.slvlr` / `.slvls` data.
7. Reload restores the same persistent cart UUID, body, seats, graph links, and station state.
8. A second run after reload completes, and client boarding is checked separately.

Static archive inspection confirms available APIs and native compatibility only. Mark any of the preceding runtime checks that were not performed as unverified.
