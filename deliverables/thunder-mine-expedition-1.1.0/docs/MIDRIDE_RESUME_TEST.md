# Isolated midride save / reload / resume test

This test used a separate copy of the finished mine world on localhost port 25578. The authoritative world and all supplied MOD JARs were left unchanged. The helper JAR remained SHA-256 `ed9c8555821b1a63a417506c82668d448837395f34c39e8b2e56fc708bc6ad6e` throughout.

## Verified result

The empty native train was started through `/mine_world start`. At 51.0 simulated seconds, approximately 193 native rail blocks from the station, the server saved and stopped normally. The first reload restored the same cart UUID, run UUID, route fingerprint, accumulated distance and elapsed time. A supported native six-axis constraint held the body in place; no helper pose assignments were used.

The first `/mine_world resume` attempt was refused. Although the native COM position was unchanged between queries ten seconds apart, the constrained Rapier body reported a residual linear speed of approximately **0.051566 blocks/s**, just above the current **0.05 blocks/s** readiness limit. The refusal remains in the evidence. A later transport disconnection ended this first reload session without a recorded normal shutdown, so that session is not counted as normally stopped.

A second reload of the same isolated copy restored the existing interrupted journey. Native position and quaternion remained unchanged across 11.117 wall-clock seconds; residual speed was approximately **0.001559 blocks/s**. The explicit resume then succeeded, keeping the original run UUID and the original 51.0 seconds of measured time. The train physically completed the remaining route and stopped at the station:

| Measurement | Result |
| --- | ---: |
| Native rail arc length | 1,035.979212 blocks |
| Measured physical travel | 1,035.576365 blocks |
| Accumulated simulated ride time | 190.15 seconds |
| Maximum native linear speed | 13.471129 blocks/s |
| Maximum measured bearing speed | 14.043546 blocks/s |
| Maximum guide error | 0.319213 blocks |
| Final station error | 0.061877 blocks |
| Verified stationary time | 3.025 seconds |
| Helper native pose assignments | 0 |

The retry server saved all dimensions and stopped normally with exit code 0. No GUI, fake player, or fabricated passenger was used in this test.

## Remaining limits

Ordinary midride state restoration and safe native holding are verified. Explicit continuation is **not unconditionally reliable after the first reload**: the measured native constraint residual can exceed the strict readiness threshold. The successful retry must not hide the first refusal. Actual Create-seat remount followed by automatic continuation is **unverified by this no-player test**.

The numerical resume bookkeeping has six separate JUnit tests, but those tests do not prove native solver readiness. The log-extraction parser has seven tests and deliberately reports the combined attempts as containing a failure even though the retry contains a valid physical completion.

## Evidence

- `evidence/native-main/resume-copy-setup.json`: isolation, source world, helper checksum and locked copy preparation.
- `evidence/native-main/midride-resume-check/midride-before-save.log` and `.json`: actual initial ride, ordinary save and normal stop.
- `evidence/native-main/midride-resume-check/midride-after-reload.log`: first reload and real resume refusal.
- `evidence/native-main/midride-resume-check/first-reload-refusal-evidence.json`: unchanged native position and residual velocity values.
- `evidence/native-main/midride-resume-recheck/midride-after-reload.log` and `.json`: second reload, unchanged held pose, preserved native continuation and normal completion/shutdown.
- `evidence/native-main/midride-resume-recheck/midride-resume-evidence.json`: accepted physical completion of the retry.
- `evidence/native-main/midride-resume-all-attempts-evidence.json`: combined result preserving the earlier refusal.

Recheck tooling: `scripts/check-native-midride-resume.py`. It runs only the guarded `run-mine-resume-check` directory and waits for actual server readiness before sending console commands.
