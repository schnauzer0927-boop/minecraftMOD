# Native Create seat position verification

This analysis refers to the unmodified attached Create 6.0.10 and Sable 2.0.6,
and the actual Minecraft 1.21.1 sources prepared by NeoForge 21.1.228. It does
not implement a replacement seat or move a passenger with helper teleports.

## The observed mount is consistent with the original seat

The production small-course server's player data included a `RootVehicle`
compound whose `Entity.id` was `create:seat`. Its position was the native plot
cell `(20481032.5, 129, 20481032.5)`, matching the cart's bearing block at
`(20481032, 128, 20481032)` and the helper's seat block at plot-local `(0,1,0)`.
`RootVehicle.Attach` referenced the same seat entity UUID. This is actual native
mount state, rather than a conclusion based only on a player's nearby location.

For this horizontal, zero-bank commissioning track, the recorded player's feet
were at Y `76.93748982`, while the bearing center was at Y `76.62498982`. The
difference of **0.3125 blocks** is exactly what the original seat implementation
and vanilla player attachment points produce:

| Quantity | Actual source value |
|---|---:|
| Native seat entity plot Y | 129 |
| SeatEntity's entity height | 0.35 |
| Default seat passenger attachment | +0.35 |
| Standing/crouching player's vehicle attachment | −0.6 |
| Create seat additional rider offset | +0.0625 |
| Resulting passenger feet plot Y | 128.8125 |
| Coaster bearing center plot Y | 128.5 |
| Passenger feet above bearing center | **0.3125** |

The apparent feet position below the seat block's base is therefore normal for
this native Create seat. It is not evidence that mounting failed.

## Exact implementation references

- `com.simibubi.create.content.contraptions.actors.seat.SeatBlock.sitDown` creates
  a real `SeatEntity`, adds it to the level, and calls `Entity.startRiding`.
- `SeatEntity.build` sets dimensions to 0.25 × 0.35.
- `SeatEntity.positionRider` uses the passenger riding position minus the
  passenger's vehicle attachment point, and adds 0.0625 plus a creature-specific
  adjustment. The extra adjustment for a player is zero.
- Minecraft `EntityAttachment.PASSENGER` falls back to the entity's height.
- Minecraft `Player.DEFAULT_VEHICLE_ATTACHMENT` is `(0,0.6,0)`.
- Sable's `entity_rotations_and_riding.EntityMixin` runs at the tail of native
  `positionRider` and `rideTick`, and calls `EntityRidingSubLevelVehicleHelper`.
- `kickRidingEntity` transforms the passenger's plot position plus its eye offset
  through the native sublevel quaternion pose, then removes the eye offset.
  The horizontal test's yaw leaves Y unchanged; a banked/sloped course must be
  observed using this native transformation rather than assuming the same
  world-Y-only formula.

The helper's `mine_test ride_status` diagnostic additionally checks the actual
player's current vehicle and native hosting cart UUID. A negative or quiet
command probe that asks for an incorrectly serialized vehicle field is not a
valid mounting test. Running, saving, loading and continuing with a passenger
remain separate runtime gates, recorded in the main test results.
