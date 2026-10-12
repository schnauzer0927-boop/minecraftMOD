# Minimal native commissioning controller

The original helper sources are in `helper/src/main/java/dev/thundermine/tools/`.
This is a small-course commissioning aid for the uploaded, unchanged Simulated Coasters / Sable / Create artifacts. It is not a replacement coaster engine, and it is not the completed mine attraction.

## Integration contract

`ThunderMineTools` registers `NativeTestRideController.register(NeoForge.EVENT_BUS)` once. The helper's explicitly guarded `CoasterTestService` builds the four-anchor, radius-24 test circuit only in the fresh Overworld named `Thunder Mine Native Test`, creates the genuine Sable/Simulated Coasters cart and existing Create seat, then calls `armStationHold(level, cart)`.

Commands use:

- `mine_test build`: build the bounded native test course.
- `mine_test start`: run the explicit two-circuit commissioning test.
- `mine_test stop`: cancel and stop actual native velocity in place.
- `mine_test status`: inspect construction and actual native measurements.
- `mine_test remove`: remove only the helper's own test objects, subject to occupancy checks.

The ride controller does not operate in previous Coaster Engineering worlds or an incompletely committed native construction. Its start also verifies four native edges with degree-two anchors and the bearing's engagement state; false/absent internal disengagement flags alone are insufficient.

## Physical motion and measurement

The controller listens at LOWEST priority after the supplied MOD's normal rail-guide callbacks. In each native pre-physics callback, it obtains the nearest **existing** graph hit and aligns its tangent with the authored commissioning direction. It changes actual tangential velocity with bounded acceleration (2 blocks/second²) and braking (3 blocks/second²), reaching a test target of 4 blocks/second. The supplied native track guide, gravity, collision system, angular constraints, and Rapier stepping still calculate position and orientation.

There are **no helper pose assignments or cart teleports during operation**. The native post-physics callback records actual world position, bearing center, quaternion orientation, native linear velocity, travelled 3D distance, elapsed simulated seconds, acceleration, and native engagement.

The four-quarter-curve test circuit permits winding measurement around its known center. Winding is derived from successive actual positions, including atan2 branch-cut handling. Reverse travel reduces net winding instead of inventing laps; distance travelled alone cannot complete a circuit. This method is deliberately limited to this commissioning circuit, not advertised as a universal course-distance solver.

A discontinuity larger than `8 blocks/second * native timestep + 0.10 blocks` fails the run before adding it to measured distance or winding. Invalid poses, missing track hits, disconnected guide state, and a 180-simulated-second timeout also fail commissioning. None of these failures is silently reported as a pass.

At the second measured station approach, braking targets a stop about 0.12 blocks before the original station point. Pass criteria require winding within 0.025 radians of two circuits, station distance no greater than 0.65 blocks, native speed at most 0.05 blocks/second, and three consecutive simulated seconds of a held stop. This is an explicitly tolerated physical stopping envelope; the helper does not force a mathematically exact station coordinate.

A native collision problem can prevent movement and ultimately fail by stall/timeout. This source does not claim a verified complete swept-volume clearance or passenger-camera test before an actual game run.

## Evidence and reload

Log markers are:

- `MINE_NATIVE_PARKED`.
- `MINE_NATIVE_TEST_STARTED`.
- `MINE_NATIVE_SAMPLE` once per 20 game ticks, with actual pose and velocity.
- `MINE_NATIVE_TEST_PASSED` with measured distance/time and stationary seconds.
- `MINE_NATIVE_TEST_FAILED`.
- `MINE_NATIVE_RELOAD` with persistent cart UUID, saved game tick, actual read-back pose/velocity and saved-position delta.
- `MINE_NATIVE_RESTORE_REFUSED` for invalid/missing closed topology or invalid saved measurement data.

The evidence is stored in the helper-owned `thunder_mine_native_test` child of Sable's native cart `user_data`. Existing user-data siblings, including the uploaded MOD's train links, are preserved. Unchanged Sable serializes this data through its normal world save path.

A restarted server never automatically resumes an unfinished commissioning run. A saved PARKED/PASSED state is restored only if the native cart UUID matches and the actual reloaded body is stationary, engaged and near its station. Restoring PASSED additionally requires the original recorded two-lap winding, measured distance and three-second stationary hold. Old measurements are preserved rather than overwritten with zeros.

This does not infer a successful reload from the presence of an NBT field alone. A real reload log and second physical start are required runtime evidence.

## Standalone verification completed

`NativeCircuitProgress` has no Minecraft or MOD dependencies. It was compiled with the installed Java 21 JDK and tested with the original dependency-free test entrypoint:

```sh
javac --release 21 -d build/native-progress-test \
  helper/src/main/java/dev/thundermine/tools/NativeCircuitProgress.java \
  helper/src/test/java/dev/thundermine/tools/NativeCircuitProgressStandaloneTest.java
java -cp build/native-progress-test dev.thundermine.tools.NativeCircuitProgressStandaloneTest
```

Result: **8 scenarios, 26 assertions passed**. The tests cover two actual sampled circuits in both directions, atan2 wraparound, reverse movement, rejected-jump nonmutation, invalid values/timesteps, measurement continuation from a reloaded real position, and the controlled stopping offset.

These arithmetic tests are separate from compiling the full helper against NeoForge / Minecraft and from launching the uploaded MODs. They do not prove native startup, completed rides, seat boarding, station braking, or native file reload. Those checks require the normal supported runtime and remain unverified until separately recorded.

## Guarded Create-seat boarding

The optional `mine_test board` command is implemented by `NativeBoarding` and uses the unchanged `SeatBlock.sitDown` API. It requires a real player within five world blocks of the genuine cart, an empty original brown Create seat, a successfully committed course, and an engaged native cart actually held at its station with speed at most 0.05 blocks/second. It refuses another vehicle, a moving train, a missing seat, a distant player, or an incomplete course.

After the existing Create interaction, it verifies that the player's actual vehicle is `create:seat` and that the existing Simulated Coasters vehicle-chain resolver identifies the same native cart UUID. An unexpected result is cancelled. The helper never substitutes a camera position for mounting. `MINE_NATIVE_BOARDING` records the player, cart, seat entity, actual vehicle type, native host UUID, game tick, world position, and global plot cell.

Optional `mine_test ride_status` reads the actual vehicle and native host cart again on a later tick without mounting or moving anything. Successful boarding still needs an actual run/screenshot; compilation alone is insufficient.

Normal right-click boarding is also available. The Create seat is a half-block cushion; the station-view screenshot's crosshair was slightly above its top edge. From the initial platform location, aim a little farther down at the brown cushion rather than the bearing or air above it.
