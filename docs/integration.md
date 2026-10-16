# Spelling Creator integration

The board is a companion to [Spelling Creator](https://spellingcreator.org), the lesson maker in the
sibling repository. Nothing is wired up yet. This page records the plan so the pieces built now point
the right way.

## No shared code across the license line

Spelling Creator is AGPL and its `@spelling-creator/core` package is JavaScript. This app is MPL and
Kotlin. The two connect through formats and HTTP, not code:

- **OSLF lesson files.** The
  [Open Spelling Lesson Format](https://github.com/playforge-coding/open-spelling-lesson-format) is a
  separate, method-neutral JSON format with a published schema. The board will read it directly with
  kotlinx.serialization. A lesson becomes a prompt shown above the board; the speller spells the
  answer; the partner advances.
- **The hub API.** Spelling Creator's interactive mode already turns a lesson into steps and saves
  typed answers privately to the speller's account. The board can fetch a lesson by hub link, be
  opened from the Practice tab, and post answers to the same endpoint with the user's session.

If real shared logic is ever needed, the OSLF reader can be extracted into a small permissively
licensed package owned by the same organization. It is not needed to start.

## What the current code already allows for

- `BoardSession` takes the text the speller produces and exposes it as `BoardState.text` and
  `currentWord`, which is what a lesson answer box needs.
- `Feedback.SAY` is the only path to speech, so a lesson's "read the question aloud" step can reuse
  the same voice.
- Layouts are data (`BoardLayout`), so a lesson that wants a three-board stencil or a yes/no board can
  ship one.
