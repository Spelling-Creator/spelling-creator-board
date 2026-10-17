# Live sync with a Practice page

A letterboard session keeps the board in step with Spelling Creator's Practice page on another
screen. The partner runs the lesson on a computer or a classroom display; the speller answers on
the tablet. The step on the tablet follows the computer, and the sentence being spelled appears in
the computer's answer box as it is spelled.

The board side is built and tested. The hub side is not written yet; this page is the contract
for it.

## Roles

- **Host**: the Practice page in a browser, signed in to the hub. It owns the lesson and the step,
  shows a share code, and records the answers (through the existing responses endpoint, since it
  is the signed-in side).
- **Board**: this app. It joins with the code only, no account, and follows. It may ask to move a
  step; the host decides and tells everyone where they are.

## Transport

One WebSocket per participant at `wss://<hub>/practice/<code>`, served by the hub's Worker next
to `/collab/<code>`. Messages are JSON text frames, one object each, with a `type` field. Any
other field is optional, so either side can add fields later without breaking the other, and an
unknown `type` is ignored.

The board opens the socket with no query string. The host opens it with `?create=1&token=<jwt>`
like a collaboration session. The hub keeps the socket alive by answering WebSocket pings; the
board sends one every 25 seconds.

`<code>` matches the collaboration rule `[A-Za-z0-9_-]{1,128}`. Six to eight characters is enough;
the code is the only secret, so the room should not accept a second host and should end when the
host leaves.

## Messages

Board to host:

| Type     | Fields           | When                                                                                   |
| -------- | ---------------- | -------------------------------------------------------------------------------------- |
| `join`   | `client: "board"` | Right after the socket opens.                                                          |
| `text`   | `text`           | Every time the sentence on the board changes, including when it clears. Show it in the answer box. |
| `answer` | `text`           | The partner tapped Done on a question. Record it as the answer to the current step; then move on and send `state`. |
| `step`   | `step`           | The partner tapped Previous or Next on the board. Move if you agree, then send `state`. |

Host to board:

| Type      | Fields                      | When                                                                           |
| --------- | --------------------------- | ------------------------------------------------------------------------------ |
| `hello`   | `lessonId?`, `step?`, `text?` | On join, with whatever is open. No `lessonId` means no lesson is open yet.    |
| `state`   | `lessonId?`, `step?`, `text?` | Whenever the lesson, the step or the answer box changes, and after every `answer` or `step` from a board. |
| `removed` | `message?`                  | The host ended the session or removed this board.                              |
| `error`   | `message?`                  | The hub refused the join (bad code, no such session).                          |

`step` is the index, from 0, in the walkthrough that `buildInteractiveSteps` produces in the hub
and `LessonSteps.build` produces here: a section's material as one step, then each question as
its own. Both sides compute it from the same document, so an index means the same screen on both.

`text` in a host message is the answer box's content. The board takes it only onto an empty board
(a board that just joined mid-answer), since otherwise the board's own words are the source.

A message the hub relays should carry only what is listed; the board ignores the rest.

## What the board does

- **Join**: Lessons sheet, "Join a letterboard session", enter the code. The hub comes from the
  Hub address setting, or from a pasted link's own hub.
- **Follow**: on `hello` or `state` with a `lessonId` the board loads that lesson from the same hub
  (public `GET /lessons/<id>`) if it is not already open, and jumps to `step`. A new `state` moves
  it again. No `lessonId` closes the lesson on the board and shows "waiting for the host".
- **Report**: every change to the sentence is sent as `text`; Done sends `answer`; Previous and Next
  send `step`. A move the host asked for is applied without echoing a `step` back, and the board's
  own advance after Done is not sent either, since the host answers the `answer` with a `state`.
  (The board does advance locally right away so the partner is not left waiting on the network.)
- **End**: `removed`, `error`, or a dropped connection end the link and say why. The lesson stays
  on the board so the partner can carry on alone. Leave is in the lesson panel and the sheet.

The implementation is `PracticeLink` in the shared module, which listens to the session like a
view does and speaks the messages above over an expect/actual WebSocket (`OkHttp` on Android,
`URLSessionWebSocketTask` on iOS). `PracticeLinkTest` plays the host against a fake socket.

## Suggested hub implementation

A Durable Object per code, like the collaboration room but far simpler: no Yjs, no admission,
one host and any number of boards. Keep the last `state` so a late board gets it in `hello`.
Relay `text` and `answer` and `step` to the host only; broadcast `state` to every board. When the
host's socket closes, send `removed` to the boards and shut down. Rate limit joins by IP the way
`/collab` limits by user, since boards are anonymous.
