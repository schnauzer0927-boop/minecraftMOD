# Thunder Mine: native motion, station controls, and verification

The main course is an original mine attraction using the supplied Create / Simulated Coasters / Sable artifacts unchanged. The independent helper constructs native graph/plot objects and supplies motor/brake controls through existing Sable physics APIs. It does not implement a replacement position-based coaster engine.

## Actual geometry and distance

`MineNativeArc` samples each **actual native graph edge** 128 times in the route JSON's authored order, accounts for reversed native edges, and builds arc-length lookup tables. The station is the existing native factory's first-edge location at authored `t=0.02`; its canonical distance is always S0.

A moving body's nearest native graph hit and parameter `t` map back to this actual arc. Wraparound produces continuous signed distance; meaningful backward motion and discontinuous jumps fail the run. Independent 3D bearing displacement and native simulated time are also measured. The controller does not use a circular shortcut, block-to-block motion, or an exaggerated speed display.

A completed train stops near the absolute canonical station S0 rather than relative to its previous parking location, preventing cumulative stopping drift over repeated tours.

## Motors, gravity and speed

Each authored outgoing segment uses its own target speed, acceleration and braking. The final 35% of a segment blends into the following node's settings with a smoothstep curve. Speeds are native blocks/second; km/h equivalent is `speed * 3.6`.

The helper changes genuine native tangential velocity with bounded net acceleration/braking and feed-forward compensation from Sable's actual configured gravity. The original native guide, collision solver, rigid-body angular constraints and Rapier stepping still calculate position and orientation. Native curves also limit requested speed using a 7 blocks/second² centripetal-acceleration governor, with an upper requested speed of 13.5 blocks/second.

Station approach uses 6–8 blocks/second² braking, targets a stop approximately 0.10 blocks before S0, and requires actual native speed at most 0.05 blocks/second within 0.65 blocks of the station for three consecutive simulated seconds. No helper pose assignment or per-tick teleport performs the run or the station stop.

Telemetry records native speed, actual travelled 3D distance, arc progress, elapsed simulated time, curvature, acceleration, guide error, ahead-chunk availability, orientation and real seated players. The native arc length and the bearing's travelled distance can differ slightly because the constrained bearing is offset above/beside a banked rail's centerline; neither is substituted for the other.

## Native station buttons

The main world includes ordinary physical buttons at:

| Function | Block coordinates |
|---|---|
| Board a free seat | `178 84 -2` |
| Start one manual circuit | `178 84 0` |
| Emergency stop | `178 84 2` |

Button interaction only operates in the committed `Coaster World - Thunder Mine Expedition` world, requires an actual button block and nearby server player, and preserves vanilla button pressing/sound. It needs no operator command permission.

Boarding calls unchanged Create's `SeatBlock.sitDown` on a real empty brown seat in the native mine-car plot, then verifies the actual `create:seat` vehicle and matching native host cart UUID. Two original Create seats are provided at plot-local `(0,2,0)` and `(0,2,1)`. A player merely standing in or near the plot does **not** count as a seated passenger.

The first automatic departure requires a real seated player and a three-second station wait. Return holds for ten seconds and dispatches again only while a real seated player remains. Shift uses existing Create/Sable dismount behavior. A manual console commissioning start can run an empty train for one circuit; `validate(level, laps)` supports one through three circuits for controlled testing.

## Emergency and interrupted reload

Emergency stop, failed native guidance and interrupted saved rides latch the controller. A supported Sable generic constraint locks the existing body to its **actual current pose** using all six native joint axes. This prevents a velocity-only stop from creeping down an incline under gravity. It uses the original native physics constraint implementation rather than repeated pose assignments.

The train is not teleported home and an interrupted run is not marked complete. An explicit station START may release a manual STOP latch only when the actual train is stationary, engaged and already within the canonical station envelope. A failed train never automatically resumes or invents a return. A manual mid-course STOP remains latched until an explicit, validated resume; ordinary interrupted saves use the remount flow below.

## Normal persistence and reload evidence

The helper-owned `thunder_mine_expedition` child in Sable cart `user_data` stores route fingerprint, persistent cart UUID, phase, measured distances/time, native arc length, continuous progress, completed laps and station-hold measurements. Existing MOD train-link data is preserved.

Normal Sable world save/stop serializes this information along with the actual cart blocks, seats and quaternion pose. On restart, PARKED/PASSED is restored only when the actual native UUID/fingerprint, stopped station position, native engagement and speed match. A saved pass additionally requires valid recorded physical distance/progress/laps/hold evidence. Changed routes or interrupted runs stay latched.

Log markers include `MINE_MAIN_RIDE_STARTED`, `MINE_MAIN_SAMPLE`, `MINE_MAIN_RIDE_PASSED`, `MINE_MAIN_RELOAD`, `MINE_MAIN_BOARDING`, `MINE_MAIN_BUTTON`, `MINE_MAIN_NATIVE_HOLD` and explicit failure/refusal markers.

## Verification boundary

Source compilation, arithmetic tests and the earlier small native commissioning circuit do not prove the new main route, body, scenery or passenger view. The completed-world test report must separately record actual main-course runs, station return, native emergency-hold stability, seat boarding, repeated tours, saved UUID/pose read-back and real client screenshots. Items not actually performed must remain unverified.


## Ordinary mid-ride saves and resume

A normal save while RUNNING, BRAKING or HOLDING retains the original run UUID, native continuous progress, travelled distance, elapsed simulation time, lap target, speed extrema and station-hold bookkeeping. Evidence is updated on every native post-physics step while running, so a normal save cannot leave it one console-log interval behind the actual cart.

On reload, the unchanged native body is held at its actual position. The controller validates the saved UUID and route fingerprint against the live course, native arc length, guide engagement, loaded chunks and finite/bounded measurement totals. It permits at most 1.5 blocks of actual saved/restored pose or arc difference; this accounts for small native reload warm-up or rounding and is recorded without resetting the journey. Missing/mismatched evidence remains safely refused. Disconnected wall-clock time is not added to ride simulation time.

After a genuine Create-seat remount, an ordinary-save INTERRUPTED journey automatically resumes after three simulated seconds. Both the existing seat vehicle and the correct native cart UUID are required; standing near a cart does not count. The controller releases its own native all-axis hold, starts its existing motor smoothly from zero, and continues toward the original canonical station target. It does not create a new lap, run UUID, departure location, or passenger-camera substitute.

The same guarded Create boarding helper permits nearby remounting of an eligible held mid-course train. Normal right-click boarding remains available. A debugging operator can invoke `/mine_world resume` to explicitly resume eligible saved holds or a genuine manually stopped journey. STOPPED and FAILED are never resumed automatically; a failed guide, changed route or missing chunks cannot be bypassed by that command. Shift retains the original Create/Sable dismount behavior; dismounting at the station is recommended.

The pure resume-bookkeeping tests independently verified six cases: preserving distance/time, canonical wraparound, native rounding, changed-geometry refusal, position-jump refusal, impossible saved values, and retaining an in-progress station hold. These arithmetic tests do not substitute for an actual mid-course save/reload/remount test; the final runtime report must record that separately.
