# Feedback design

The problem with most AAC apps is a gap: the speller touches, and nothing tells them unambiguously,
within the same instant, that the letter went in. So they press again, and now there are two. A
physical letterboard closes that gap with a rigid stop under the finger and a partner saying the
letter out loud right away. The app has to replace both, and the replacement has to be faster than
the speller's next impulse.

Everything below is implemented; the settings mentioned are in the app's settings screen and stored
per device in `BoardSettings`.

## Commit on touch-down

A laminated board registers on contact. Most AAC apps commit on release, so the feedback arrives a
beat late and a slid finger produces nothing. The default here is touch-down. Touch-up remains a
setting for spellers who rest a finger and slide to the letter they want; in that mode the resting
cell is highlighted and the letter commits where the finger lifts.

## One commit, every channel, same frame

On `KEY_COMMITTED` the apps do all of this at once:

- **Sound.** The letter name is spoken from a pre-rendered clip. Live text to speech takes long
  enough to start that it is exactly the lag that triggers a second press, so each phrase on the
  current board is synthesized once (to a file on Android, to a PCM buffer on iOS) and played from
  memory after that. Until a clip exists, the live engine is used so nothing is silent. A new letter
  cuts off the previous clip so fast spellers never fall behind. Space reads the whole word back.
- **Haptic.** The generator is prepared ahead of time and fired on the same event. Accepted and
  rejected touches use different patterns. This matters when the speller holds the device, less when
  the partner does, so it is a toggle.
- **Visual.** The cell lights up and holds it for 350 ms, long enough to be perceived. On the
  stencil board the cutout turns amber with a soft glow behind it; on plain keys the key fills with
  the accent color. The letter also pops up large beside the sentence strip, like a partner holding
  it up, and the strip itself updates. The strip is adjacent to the board at a large type size so
  the eye confirms it without looking away.

## The repeat gets an answer, not silence

If the same cell is hit again inside the refractory window (400 ms by default, 0 to 1500 ms in
settings), the engine reports `KEY_REPEATED`: the letter is not added, an orange ring appears
around it, the reject haptic plays, and nothing is spoken. The speller learns the rule because the app
responds differently to each case rather than responding to neither. Double letters still work by
lifting and touching again after the window.

## Every touch does something

Hit testing snaps gaps and padding to the nearest key, so there are no dead zones on the board. A
touch that lands on the view but outside the board reports `OFF_BOARD` with a reject haptic.

## First touch wins

While one pointer is down, every other pointer is ignored. That handles a steadying palm and the
partner's hand on the screen edge. On iPad, stylus-only mode ignores finger touches entirely, since
many spellers point with a pencil while the partner holds the tablet.

## Delete gets the same treatment

Backspace and Clear report what they removed and play the confirm haptic, so a wrong letter never
silently disappears.

## Latency budget

Visual on the touch-down frame. Audio under 50 ms. Haptic under 20 ms. That rules out any design
where the UI waits on something asynchronous before responding, which is why the session is
synchronous and lives on the UI thread, and why the raw touch APIs are used instead of gesture
recognizers on both platforms.

## Not built yet

- Dwell selection (hold to choose) for spellers who cannot tap cleanly.
- Recording the partner's own voice for the letter clips.
- A printable stencil at true size for the exact device, so a cut or 3D printed overlay restores
  the tactile stop for spellers who come from stencil boards.
- Custom boards.
