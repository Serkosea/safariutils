# Sparkling count recovery from Minecraft logs

`/safari importsparkling` explicitly scans dated .log/.log.gz archives in this instance. It uses the shared authoritative catch parser, username identification and confirmed main-Hypixel connection state. Unknown account/server segments and Alpha are excluded. Only catches credited to the current username count: historical usernames and shared party credit cannot be proven safely from this evidence. Current latest.log is excluded until rotation supplies a date.

Counts are lower bounds merged with max(existing, observed), not added to history or API imports. Repeating imports cannot inflate totals. Date/time/species event keys deduplicate mirrored archives and repeated lines; simultaneous identical events may undercount deliberately. No synthetic runs, PBs, feathers, or last-catch timestamps are created. This does not recover all lifetime duplicates without complete compatible logs.

Reads run on the existing bounded worker pool, with one active import, at most 10000 archives, 512 million decoded characters overall, 32 million per file and 65536 per line. Corrupt/oversized individual files are discarded and reported as skipped; overall limit failure applies nothing. No files are uploaded or altered. Application occurs on the client thread only if the account/connection remains the same and persistence remains enabled. Future-schema-protected collection stores are not changed.

Regression fixtures cover compressed/plain overlaps, repeated lines, Alpha exclusion, other players, missing identity, active-log exclusion, and repeated scan stability. Live import results remain limited by available log evidence.

The gear action is Import Sparklings. The collection tab offers an opt-in recovery prompt once per launch when imported aggregate duplicates exceed per-species duplicates. Equal totals, absent imported totals, and Alpha do not prompt. Importing never changes run history. Regression coverage includes these comparison cases.
