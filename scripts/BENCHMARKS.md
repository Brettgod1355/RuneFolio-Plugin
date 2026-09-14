# Offline component benchmarks

`gradle performanceBenchmark` runs with a 512 MiB heap in headless mode. It does not start RuneLite, connect an account, upload images or make application network requests. Gradle may download the normal declared build dependencies. The optional CI workflow also exposes a manual run and runs when the measured components or harness change.

The harness reports 30 samples after 10 warmups for queue reload, batch snapshot, acknowledgement, four synthetic snapshot-shaped events, and JPEG encoding at three image sizes. It checks queue counts after recovery. Fixture creation for the four-event burst is included; other queue fixture initialization and integrity assertions are outside the timed intervals. Raw pixel byte counts are width × height × 4, not measured heap usage. No test data, screenshots or profiles are uploaded as artifacts.

These are component measurements, **not** live collector/full-sync timings, an allocation profile, client FPS, ConfigManager/disk performance or backend capacity. No network retry behavior is simulated by this harness. Shared-runner timings are descriptive; there are deliberately no timing pass/fail thresholds.

Use a real opted-in development session to profile client-thread collection and ConfigManager persistence, and an explicitly isolated backend for outage/recovery and server-load testing. Never collect credentials, real screenshot contents or private player payloads into benchmark output.
