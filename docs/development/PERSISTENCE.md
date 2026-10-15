# Saved-data ownership and recovery

## File publication

Normal JSON saves use `AtomicFiles.writeString`: a unique same-directory staging file,
serialized destination writes, then replacement (atomic move when supported). Migration
and release backups instead use `AtomicFiles.copyOnce`, which stages the source and never
replaces an existing backup. Both clean their staging files on handled failures.

These operations reduce partial-file exposure. They do not promise durability across power
loss, validate historical backup contents, or coordinate unrelated external file editors.
An existing backup is deliberately not overwritten merely because a later source differs.

## Protected sources

Unknown schemas and partially readable stores must not be normalized over the original.
Stores retain readable state where possible and route new saves to a per-session
`RecoverySnapshot` under their recovery backup directory. The original remains the source
of truth for information this build could not interpret. Syntax-invalid file handling may
use the store's existing quarantine behavior; inspect that store before offering recovery.

Recovery snapshots are not silently merged or automatically restored. Any reconciliation
must preserve the protected source, distinguish old entries from new session additions,
and use the relevant store's schema/identity rules. A successful recovery write means the
working state was saved separately, not that the original was repaired.

Bridge and ChatUtils field decoding also validates nested list entries. Unknown nested
enum values, wrong JSON types, and unknown object fields protect the source instead of
being silently removed by sanitization. Other readable top-level settings still load.
Nested lists are not identity-merged on save; an unsupported list remains in the protected
original and uses defaults in working state until it can be reconciled safely.

## Chat history

SerkoAddons stores at most 1,000 styled player messages. Retention is keyed by logical
message ID, while GUI-object associations are weak provenance references. Clearing a view
does not erase provenance or make old messages current. Compaction removes the retained
logical entry even when Minecraft reinstates a previous GUI representation.

Protected chat-history saves include only current-session additions in recovery. Ordinary
history saves at shutdown and channel-availability/disconnect boundaries. Dirty history also
checkpoints at most once per minute through the shared bounded worker, with one outstanding
snapshot. The client thread copies retained components; encoding and atomic file replacement
run in the background. This reduces, but cannot eliminate, loss after a crash.

Each snapshot has a monotonically increasing in-session revision. `OrderedSnapshotWriter`
serializes commits and rejects revisions older than or equal to the last successful commit.
A delayed checkpoint cannot overwrite a newer disconnect/shutdown save; a failed write does
not advance the committed revision. Protected-source recovery uses the same ordering. The
worker is closed after the final synchronous shutdown save; no per-message disk writes occur.

## Validation

Headless regressions cover concurrent writes/backups, destination conflicts, schema and
field rejection, unknown-field retention, protected recovery, and logical chat retention.
They do not substitute for migration tests on representative user backups or in-game
shutdown/reload checks. Never test a destructive migration directly on installed user data.
