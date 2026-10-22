# Critical playback startup failure — audio pipeline

Severity: **P1** (ordinary video playback fails). Status: Root tester observed the H.264/AAC fixture fail on fresh device launch; this auditor independently confirmed the cause using the current app source and locally resolved Media3 1.11.1 bytecode. The temporary instrumented probes are prepared; their results are pending the root tester's run.

Observed by root tester: fixture `content://media/external/video/media/119919` produces `ExoPlaybackException: Unexpected runtime error`, caused by `IllegalArgumentException: The source buffer is this buffer` at `VideoDynamicsProcessor.queueInput(VideoDynamicsProcessor.kt:67)`. Recovery retries, then compatibility fallback; UI becomes black.

Primary source locations:

- `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/player/VideoDynamicsProcessor.kt:64` calls `replaceOutputBuffer(inputBuffer.remaining())` before checking for empty input.
- Normal mode copies input with `output.put(inputBuffer)` at line **67**.
- Volume Leveling/Night mode also copies the remaining input with `output.put(inputBuffer)` at line **82**, even when no bytes remain.
- `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/player/VideoPlaybackManager.kt:421`–`:427` creates and installs this processor for every normal video player, including Normal sound mode. It is active for PCM16 because onConfigure returns the input format even when effects are off.

## Exact call sequence

1. Configure and flush a fresh VideoDynamicsProcessor for PCM16 stereo. BaseAudioProcessor's private reusable `buffer` still points to the shared `AudioProcessor.EMPTY_BUFFER` (capacity zero).
2. DefaultAudioSink.processBuffers calls `audioProcessingPipeline.getOutput()` before it queues the first decoded PCM chunk. This is normal pipeline draining behavior, not malformed media.
3. AudioProcessingPipeline.getOutput finds no final output, calls `processData(AudioProcessor.EMPTY_BUFFER)`.
4. processData invokes VideoDynamicsProcessor.queueInput with that shared EMPTY_BUFFER, even though it has zero remaining bytes.
5. queueInput calls `replaceOutputBuffer(0)`. BaseAudioProcessor reuses its current zero-capacity `buffer`, so its returned output object is the same object as the input: `output === inputBuffer === AudioProcessor.EMPTY_BUFFER`.
6. Normal mode executes `output.put(inputBuffer)` and Android's ByteBuffer implementation rejects copying a buffer into itself. Volume Leveling reaches the same illegal self-copy in the trailing `output.put(inputBuffer)` after its sample loop does zero iterations.
7. The unchecked exception escapes into the audio renderer and becomes ExoPlaybackException Unexpected runtime error.

This explains why existing unit tests miss the failure: their first call queues a nonempty input chunk, so replaceOutputBuffer allocates a distinct nonzero buffer. A sequence of nonempty queueInput/getOutput/consume calls should pass and is useful as a control. The failing startup sequence includes an output poll, and therefore an empty queue, before the first real input.

## Temporary probe coverage

File: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/androidTest/java/com/example/ymediaplayer/player/FullQaAudioPipelineProbeTest.kt`

1. normalSequentialPcm16StereoChunks: twelve nonempty chunks, consume each output, verify PCM bytes preserved.
2. volumeLevelingSequentialPcm16StereoChunks: same sequential consumption with leveling enabled.
3. normalPipelineStartupEmptyDrainThenSequentialChunks: real AudioProcessingPipeline output poll before first input; expected to fail at current line 67.
4. volumeLevelingPipelineStartupEmptyDrainThenSequentialChunks: same startup in leveling mode; expected to fail at current line 82.

The probes assert correct behavior rather than expecting the bug, so the two startup tests should fail on the current source. Production source is unchanged. No Gradle/device command was run by this auditor.

## Local dependency evidence

The current Media3 dependency is 1.11.1 in `gradle/libs.versions.toml:16`. Decompiled local dependency bytecode is saved beside this report:

- `AudioProcessingPipeline.javap.txt`: getOutput calls processData(EMPTY_BUFFER); processData invokes queueInput even for empty input.
- `DefaultAudioSink.javap.txt`: processBuffers calls pipeline.getOutput before queuing the current decoder input.
- BaseAudioProcessor was inspected with javap: its constructor initializes buffer and outputBuffer to EMPTY_BUFFER, and replaceOutputBuffer reuses a buffer whose capacity is >= the requested size, including 0.

Suggested fix for a later authorized fix pass: Treat empty input as a no-op before calling replaceOutputBuffer, and avoid the remainder put when there are no bytes. Retain pipeline-startup tests as regression tests. Recheck seek, flush and end-of-stream paths after correction.
