# Protocol 3 cutover — 11 November 2026

Explicitly authorized after both clients updated. Deployed Worker version:
`b216a051-85ba-4012-9dee-b3eaf0802698`.

`SYNC_MIN_PROTOCOL=3` now rejects missing, version-1 and version-2 sync headers
with HTTP 426 and `sync_update_required` before authentication or Durable Object
admission. This does not gate player lookup or market endpoints.

Six focused transport tests and deployment dry-run passed. Live checks confirmed
all three rejection cases, an authenticated protocol-3 WebSocket welcome in an
isolated test room, and healthy service status. Earlier rollout documents describe
the temporary legacy-enabled stage, not the current configuration.

The gate prevents new obsolete sync connections. Do not interpret it as a
retroactive per-message cutoff for a socket admitted before the deployment;
any surviving old socket is rejected on its next connection attempt.
