# Spelling Creator integration

The board opens lessons from a [Spelling Creator](https://spellingcreator.org) hub and walks
through them the way the hub's own interactive mode does, with the letterboard as the answer box.
It is read only and needs no account.

## What it does

- **Lessons** in the top bar opens a sheet with the hub's published lessons and a field for a
  lesson link or id. Tapping one puts the lesson on the board.
- The lesson panel shows one step at a time: a section's material (text, spelling words, picture
  captions, a VAKT activity set apart in red), then each of the section's questions on its own,
  numbered from 1 within the section, with the question's type tag (K, M, SO and so on, the Open
  Spelling Lesson Format's defaults). Empty sections are skipped. This is the same walkthrough
  `buildInteractiveSteps` produces in the hub, re-implemented in Kotlin in `LessonSteps`.
- On a question step the speller spells on the board and the partner taps **Done**. The answer is
  kept with the lesson and in the history, the board clears, and the next step comes up. **Show
  answer** reveals the author's answer, marked as examples for the semi-open types.
- **Read aloud** speaks the step through the letter voice: the section name, then the material or
  the question.
- Nothing is sent back to the hub. Answers stay on the device, in the lesson run and in the history,
  and can be shared from there like any sentence.

## Self-hosted hubs

Settings has a **Hub address**, defaulting to `https://spellingcreator.org`. A self-hosted instance
goes there instead, and the lesson list and bare ids resolve against it. A pasted link names its
own hub (`https://spelling.example.org/hub/<id>`), so a link from any instance opens no matter what
the setting says. Links with or without `https://`, with or without `/practice`, and bare ids all
work; see `HubLinks`.

## How it talks to the hub

Two public endpoints, both plain GET with no credentials, served from the hub's origin:

| Request              | Used for                                                            |
| -------------------- | ------------------------------------------------------------------- |
| `GET /lessons`       | The published lessons: id, title, author, section count.            |
| `GET /lessons/<id>`  | One lesson with its editor document (`title`, `sections[].blocks`). |

`HubClient` in the shared module makes the calls over a tiny expect/actual `HttpClient`
(`HttpURLConnection` on Android, `NSURLSession` on iOS) and hands results back on the main thread.
`LessonParser` reads the document loosely with `JsonElement`, keeping only words: a text block's
rich-text paragraphs become lines, formatting and footnotes are dropped, a picture keeps its caption
without the credit, a question's prompt may be a string or rich text, and answers come from
`answer` or `answers[]` depending on the type. Anything unexpected is skipped so old or hand-written
lessons still open.

## No shared code across the license line

Spelling Creator is AGPL and JavaScript; this app is MPL and Kotlin. The two connect through this
HTTP boundary and through formats, never shared code. The
[Open Spelling Lesson Format](https://github.com/playforge-coding/open-spelling-lesson-format)
is the method-neutral file the hub can export; reading OSLF files directly is a natural next step
and would reuse `LessonParser`'s block model.

## Not done yet

- Pictures are shown as captions only. Loading the images needs the hub's image URLs and a cache.
- Saving answers to a hub account (`POST /lessons/<id>/responses`) needs a Supabase sign-in.
- Drafts need the author's credentials and are not reachable.
- Opening a `spellingcreator.org/hub/...` link from another app straight into the board (deep links).
