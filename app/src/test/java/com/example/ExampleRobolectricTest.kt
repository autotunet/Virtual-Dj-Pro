package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BiquadFilter
import com.example.audio.DrumSound
import com.example.audio.DrumSynthEngine
import com.example.audio.EffectsChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Virtual DJ Pro", appName)
  }

  @Test
  fun `drum synth generates high fidelity cymbals and percussion`() {
    val drumSynth = DrumSynthEngine(44100)
    val crashSample = drumSynth.getSample(DrumSound.CRASH_PIONEER)
    val rideSample = drumSynth.getSample(DrumSound.RIDE_PIONEER)
    val kick808 = drumSynth.getSample(DrumSound.KICK_808)

    assertTrue("Pioneer Crash cymbal should contain audio samples", crashSample.isNotEmpty())
    assertTrue("Pioneer Ride cymbal should contain audio samples", rideSample.isNotEmpty())
    assertTrue("808 Kick should contain audio samples", kick808.isNotEmpty())
  }

  @Test
  fun `biquad filter processes audio without clipping`() {
    val filter = BiquadFilter(44100f)
    filter.setLowPass(1000f, 0.707f)
    var maxVal = 0f
    for (i in 0..100) {
      val out = filter.process(0.5f)
      if (Math.abs(out) > maxVal) maxVal = Math.abs(out)
    }
    assertTrue("Filter output should remain bounded", maxVal < 2.0f)
  }

  @Test
  fun `deck audio player seeks and scrubs accurately`() {
    val player = com.example.audio.DeckAudioPlayer("A", 44100)
    val testBuffer = ShortArray(44100 * 2 * 10) // 10 seconds of audio
    val track = com.example.data.Track(
        id = "test",
        title = "Test Track",
        artist = "Test Artist",
        genre = "House",
        bpm = 128f,
        musicalKey = "8A",
        durationMs = 10000L
    )
    player.loadTrack(track, testBuffer)

    player.seekTo(5000L) // 5 seconds
    assertEquals(5000L, player.currentPositionMs)

    player.scrubDelta(1000L) // scrub forward 1 second
    assertEquals(6000L, player.currentPositionMs)
  }

  @Test
  fun `beat matching sync aligns tempo and phase with master clock`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.audio.VirtualDjAudioEngine(context, 44100)

    val trackA = com.example.data.Track(
        id = "tA", title = "Track A", artist = "Artist A", genre = "House",
        bpm = 128f, musicalKey = "8A", durationMs = 60000L
    )
    val trackB = com.example.data.Track(
        id = "tB", title = "Track B", artist = "Artist B", genre = "Techno",
        bpm = 124f, musicalKey = "9A", durationMs = 60000L
    )
    val bufA = ShortArray(44100 * 2 * 5)
    val bufB = ShortArray(44100 * 2 * 5)

    engine.deckA.loadTrack(trackA, bufA)
    engine.deckB.loadTrack(trackB, bufB)

    // Set Deck A as Master Clock (128 BPM)
    engine.setMasterDeck("A")
    assertTrue(engine.deckA.isMasterDeck)

    // Trigger Sync on Deck B
    engine.toggleSync("B")
    assertTrue(engine.deckB.isSyncActive)

    // Deck B's effective BPM should now match Deck A's Master Clock tempo (128 BPM)
    assertEquals(128.0f, engine.deckB.effectiveBpm, 0.1f)
  }

  @Test
  fun `deck audio player calculates beat phase and bar position accurately for LED pulse`() {
    val player = com.example.audio.DeckAudioPlayer("A", 44100)
    val testBuffer = ShortArray(44100 * 2 * 10)
    val track = com.example.data.Track(
        id = "test", title = "Test", artist = "Artist", genre = "EDM",
        bpm = 120f, musicalKey = "8A", durationMs = 10000L
    )
    player.loadTrack(track, testBuffer)

    // At 120 BPM, 1 beat = 0.5s = 22050 stereo frames
    player.seekTo(0L)
    assertEquals(1, player.currentBeatInBar)
    assertEquals(0.0f, player.beatPhase, 0.05f)

    player.seekTo(500L) // Beat 2 (0.5s)
    assertEquals(2, player.currentBeatInBar)

    player.seekTo(1000L) // Beat 3 (1.0s)
    assertEquals(3, player.currentBeatInBar)

    player.seekTo(1500L) // Beat 4 (1.5s)
    assertEquals(4, player.currentBeatInBar)
  }
}
