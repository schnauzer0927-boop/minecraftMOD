# Attached coaster API investigation

This document records static inspection of the user-supplied, unmodified
`simulatedcoasters-0.1.5.jar`. CFR 0.152 was obtained from Maven Central and used
only to inspect classes. The attached JAR was not executed by the inspection
process. Decompiled source is a local analysis aid and is not part of the
original helper source deliverable. Static support is not a successful runtime
test; the small-course commissioning result must be recorded separately.

## Required environment

`META-INF/neoforge.mods.toml` identifies `simulatedcoasters`, version `0.1.5`,
Minecraft **1.21.1**, NeoForge **21.1.228 or later**, Create **6.0.10 or later**,
Sable **2.0.0 or later**, and client Flywheel **1.0.6 or later**. Minecraft
1.20.1 / Forge 47.4.10 is not the environment for these attachments. Java 21 is
required by Minecraft 1.21.1. The coaster JAR declares MIT licensing.

For the combined uploaded set, Sable **2.0.6** additionally limits Create to
**[6.0.10, 6.1.0)**. The actual tested combination pins Create **6.0.10**;
the coaster JAR's broader minimum declaration alone does not prove compatibility
with Create 6.1.0 or newer.

## Track construction

The coaster uses Create's cubic `BezierConnection`, with additional coaster
mixins for its geometry and a separate persistent graph. It does **not** use
Create's ordinary train bogies or the old Coaster Engineering motion engine.

All package names below start with `dev.silvergold.simulatedcoasters` unless
the fully qualified name says otherwise.

| Function | Exact class / public method |
|---|---|
| Registered anchor block | `SimulatedCoastersBlocks.COASTER_ANCHORPOINT.get()` |
| Track material | `CoasterTrackMaterials.COASTER` |
| Anchor state | `track.anchor.CoasterAnchorpointBlock.FACING` (default `UP`) |
| Endpoint position | `CoasterAnchorpointBlockEntity.worldCenter(Level, BlockPos)` |
| Fixed lift offset | `CoasterAnchorpointBlockEntity.setLiftBlocks(float)` |
| Save bank setting | `CoasterAnchorpointBlockEntity.setJointBankRadians(float)` |
| Add connection | `CoasterAnchorpointBlockEntity.putAnchorPeerCurve(ServerLevel, BlockPos peer, BezierConnection)` |
| Group all connections | `track.graph.CoasterTrackPropagator.runBatchUpdate(ServerLevel, Runnable)` |
| Refresh edited endpoints | `CoasterTrackPropagator.touch(ServerLevel, Collection<BlockPos>)` |
| Custom handles | `track.CoasterPeerCurveHandleLengths.putCanonical(BezierConnection, double hAtLexLow, double hAtLexHigh)` |
| Live path graph | `track.graph.CoasterPathGraphManager.get(ServerLevel)` |
| Sample actual rail path | `CoasterPathGraphManager.sampleEdgePosition(Level, CoasterPathEdge, double t)` |

The constructor is:

```java
new com.simibubi.create.content.trains.track.BezierConnection(
    net.createmod.catnip.data.Couple.create(anchorA, anchorB),
    Couple.create(startA, startB),
    Couple.create(forwardA, forwardB.scale(-1)),
    Couple.create(normalA, normalB),
    true, /* primary */ false, /* girder */ CoasterTrackMaterials.COASTER);
```

Both endpoint axes point **into** the curve. The second is therefore the
negative of the direction of travel at B. The two control handles are stored
canonically according to `BlockPos.asLong()` ordering. If their lengths differ,
map the authored A/B lengths to the lexicographic low/high endpoints explicitly.
`putAnchorPeerCurve` stores the reciprocal curve in B, generates managed fake
track interaction blocks, calls normal dirty/client notifications, and rebuilds
the coaster graph through the propagator. Do not populate a guessed NBT layout
or directly replace the graph as a substitute for real track blocks.

For an UP anchor, the base center is block + `(0.5, 0.25, 0.5)`; the default lift
is **0.5**, not zero. Lift direction can become dependent on the first incident
curve. An original generated route may choose `setLiftBlocks(0f)` before adding
curves to obtain fixed endpoint positions. Bank affects endpoint normals: the
graph's stored `rollStart` / `rollEnd` is not an extra rotation applied by the
cart frame. Supply a consistently banked normal at each authored point and
reuse that normal for both incident curves; record the bank on the anchor for
the editor. The existing `CoasterBezierParallelFrame` transports normals through
each curve; `BezierBankBlend` smooths joint bank interpolation.

Placement defaults in `Config`: maximum anchor-to-anchor chord **45 blocks**
(configurable 1–128), maximum native chain span **64 blocks** (1–128). The
placement optimizer checks a minimum endpoint curvature radius of **2 blocks**
in its suppress-neighbor-smoothing mode; that is a placement allowance, not a
recommended high-speed operating radius. A helper should retain the normal
span limit and reject its own bad geometry before registration. The native
anchor accepts at most **two** incident peers. A playable closed loop should
have every anchor connected to exactly two neighbors.

The MOD explicitly disengages a cart within **0.14 blocks** of a terminal open
path end. An unfinished route will not magically act as a safe returning loop.
Native engagement distance is at most 2.5 blocks, and re-engagement additionally
checks cart/rail normal alignment. Confirm actual engagement on every test tick.

## Real cart creation and riding

The original cart is a **Sable ServerSubLevel containing a coaster-cart block**,
not a conventional Minecraft vehicle entity. The original public spawn path is:

```java
var graph = CoasterPathGraphManager.get(level);
var edge = /* locate the actual edge by its two authored anchors */;
var point = CoasterPathGraphManager.sampleEdgePosition(level, edge, 0.02);
var hit = new CoasterPathTrackFrame.GraphHit(point, edge, 0.02);
var basis = CoasterPathTrackFrame.trackBasisAtHit(level, hit);
var q = CoasterPathTrackFrame.quaternionFromTrackBasis(basis);
var origin = CoasterCartMidTrackPlacement.plotOriginForGraphHit(level, hit, q, null);
var cart = CoasterCartSpawner.spawnMinimalContraption(level, origin, q, hit, true);
```

These classes reside in `track.graph` (`CoasterPathTrackFrame`) and `track.cart`
(`CoasterCartMidTrackPlacement`, `CoasterCartSpawner`). `spawnMinimalContraption`
allocates a real Sable sublevel, allocates its plot chunk, places the registered
cart block at plot-local zero, sets the actual pose / rotation point, calls
Sable's physics `onStatsChanged`, and installs the native track guide. A `null`
result is failure. The bearing center is path point + `0.3125 * trackUp`; the
native path sampler already accounts for the extra `0.0625 * faceUp` path lift.
Use the original APIs to prevent duplicate or missed offsets.

Place an original Create seat in the actual Sable plot, for example at local
`(0, 1, 0)`, using `cart.getPlot().getEmbeddedLevelAccessor().setBlock(...)`.
Create `SeatBlock.sitDown(Level, globalPlotCell, Entity)` is public and the seat
block's normal right-click action uses it. Sable's entity tags explicitly retain
`create:seat` in a sublevel; its Entity riding mixins transform the player to
the moving sublevel world pose. The exact persistence and seat behavior are
also recorded in [SABLE_API.md](SABLE_API.md). Do not replace the seat with a
camera teleport to claim the passenger has boarded.

## Physics, lift and station support

`CoasterCartTrackSnap.onPrePhysicsTick(ForgeSablePrePhysicsTickEvent)` is
registered by the original MOD. It resolves the real nearest graph hit, installs
or updates `CoasterTrackGuideConstraint`, applies `CoasterCartChainLiftDrive`,
and compensates Sable universal drag. The guide locks local linear Y/Z and all
angular axes; local linear X is free along the rail. Static rails therefore
retain native rigid body integration, gravity and contact behavior between
physics steps; normal running is not a per-tick pose assignment.

The coaster JAR registers anchors, cart, rivet and red balloon blocks. Static
inspection found **no dedicated coaster station, brake, booster or LSM block**,
and no public coaster-specific generation command. Create's ordinary train
station belongs to a different train system. A separate helper may provide a
station controller and launch/brake segments by applying bounded tangent
velocity changes through the real Sable physics pipeline, while leaving the
attached JAR unchanged. It must measure real world positions and velocities,
check native engagement and stop on failure; it must not teleport the cart
along an independently invented motion path.

Native chain-lift setup is available:

1. Resolve `CoasterPathGraphSpans.spanPathBetweenAnchors(level, graph, start, end, 0)`.
2. Set `anchor.commitChainLift(Direction.Axis, ChainLiftEndRef.anchor(end), span)`.
3. Power the exposed shaft with a real Create kinetic source. The lift anchor
   extends `KineticBlockEntity`; shaft axis must lie in its mounting plane.
4. `ChainLiftDriveDirection.signedChainBlocksPerSecond` derives speed from actual
   RPM: **256 RPM corresponds to 16 blocks/second**, sign comes from belt/route
   alignment. Native speed matching changes tangent speed by up to 64 or
   32 blocks/second² depending on grip state.

The span, shaft axis, signed travel direction and kinetic state use normal block
entity save/load. Do not claim a visual chain with no actual powered span is a
functional native chain lift.

## Multiple cars

`track.cart.CoasterCartTrainLinkConstraint.linkCarts(ServerLevel, ServerSubLevel,
ServerSubLevel)` and the overload with an explicit final `double centerDistance`
are public. They validate both carts, enforce at most two links per cart, reject
link cycles, record reciprocal partner UUIDs, sync the carts, and create native
Sable constraints. The link locks only its local linear X distance. Native links
can break under stretch above 0.5 blocks or excessive force above 1800 (with an
additional 0.07-block stretch threshold). Test a single cart first; only add
coupled cars after the whole track runs reliably.

Persistent sublevel user data key is `simulatedcoasters:cart_train_links`, a
list of compounds with `partner` UUID and `distance` double; the legacy singular
key is also accepted. `restorePersistedLinks(ServerLevel)` restores actual
constraints, and the MOD calls it during normal physics. Invoke the native link
method rather than writing these fields manually.

## Native saved data

| Data | Actual location / serialization |
|---|---|
| Track endpoints / curves | Normal region chunk anchor block entities |
| Anchor curves | `AnchorPeerCurves`: `Peer` long, `Bezier` compound, optional `HLo` / `HHi` handle lengths |
| Bezier | Create serializer: `Positions`, `Starts`, `Axes`, `Normals`, `Primary`, `Girder`, `Material`, optional `Smoothing` |
| Anchor editing | `JointBank`, optional `Lift`, `OpenEndExtension`; tiered rail/beam tint list |
| Chain lift | `ChainLiftShaftAxis`, `ChainLiftSpan`, `ChainLiftCommittedLength`, `ChainLiftFirstHop`, `ChainLiftCommittedSegments`, `ChainLiftTravelSign` |
| Coaster path graph | Overworld `data/simulatedcoasters_coaster_paths.dat` |
| Graph contents | `Dimensions` list; each has `Dimension` string and `Graph` containing `Nodes` and `Edges`; edges include endpoints, Bezier, rolls and tangent sign |
| Cart and passenger host | Sable native sublevel archive and plot chunk files, **not ordinary entities-only**; see SABLE_API.md |

Anchor `onLoad` repairs peer keys and recreates graph/fake interaction support.
The graph is held per dimension and SavedData marks dirty during propagation.
The anchors are authoritative physical world objects. A world distribution
must preserve the entire world, including `sublevels`, `data`, region blocks,
and any independent helper SavedData.

## Minimum commissioning gate before large construction

Use a horizontal four-quarter-circle course with radius 24 blocks:

| Node | Anchor | Forward direction |
|---|---|---|
| A | `(24,76,0)` | `(0,0,1)` |
| B | `(0,76,24)` | `(-1,0,0)` |
| C | `(-24,76,0)` | `(0,0,-1)` |
| D | `(0,76,-24)` | `(1,0,0)` |

Each quarter has handles `4/3 * tan(pi/8) * 24 = 13.2548339959`, chord
33.9411255 blocks (<45), shared world-up normal and zero bank. Build all four
arcs with the native API and verify exactly four graph edges and degree two at
every anchor. Target 4 blocks/second for the first motion test; theoretical
length is approximately 150.8 blocks and lap time roughly 37.7 seconds before
station dwell. Those figures are planning values, not observed test results.

Record the real sublevel UUID, engagement, actual world positions, actual path
completion, actual station speed and two consecutive rounds. Issue normal
`save-all flush`, stop normally, restart the same world, and verify the same
curve geometry, graph topology, cart UUID, seat block, engagement and a further
round. Only after this runtime gate succeeds should the full mine attraction
be created. Any unsupported or untested station/riding operation must remain
explicitly unverified.
