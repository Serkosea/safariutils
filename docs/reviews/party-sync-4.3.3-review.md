# Party-sync compatibility review — 11 November 2026

Compared Sparkling Mutuals 4.3.3 for Minecraft 26.1.2, 26.2 and 26.3 with
SafariUtils 3.8.2 and the deployed transport-3 contract. All 42 classes in its
`sync` package are byte-identical across the three jars. Review used extracted
bytecode/decompilation; the downloaded mods were not executed.

## Result

No blocking primary-service protocol mismatch was found in the reviewed paths.

| Contract | Reviewed behavior |
| --- | --- |
| Negotiation | Protocol header 3; efficient transport enabled from server capabilities. |
| Routine batching | A/N/U/C/F frames, up to 8 children and 4096 UTF-8 bytes. |
| Urgent state | Sparkling and coordination frames flush pending routine updates before sending. |
| Completion freshness | Full C baseline, compact F revision reference, 20-second efficient refresh; scoped resync sends fresh full C immediately. |
| Reconciliation | Pending retry guidance respected; unchanged healthy hellos suppressed; current visit/run checks retained. |
| Hotspot reports | Accepted-report acknowledgments stop retries, including equal-revision snapshots. |
| Renewal | Existing socket renewed around 8:05 with a session token; bounded timeout and socket/generation guards. |
| Upgrade requirement | Explicit 426/update-required response recognized to stop repeated upgrades. |
| Coins | Full 39-field aggregate includes held Shining Coins at index 38. |
| Objective state | Mound evidence and shared objective updates remain in the existing payload contract. |

John's separate backup provider remains independent. SafariUtils does not join it;
cross-mod state sharing therefore requires both clients to be on the primary
service. This is an intentional integration boundary, not a protocol mismatch.

## Follow-up

- The operator subsequently authorized the protocol-3-only cutover after confirming
  the updated mods. Missing/1/2 headers now receive update-required; an authenticated
  protocol-3 WebSocket welcome was verified against the deployed backend.
- A useful remaining live check is a run longer than ten minutes, one reconnect,
  a late-joining player, and a Sparkling discovery followed by another player's
  catch. Static review cannot prove in-game timing or actual packet emission.
- No additional required client payload changes were identified. Avoid adding
  polling or redundant full-state refreshes to solve issues already covered by
  replay, acknowledgments and completion resync.

The backend contract/runtime tests and SafariUtils release checks validate our
implementation; they do not execute John's client or certify all unrelated code
in his jars.
