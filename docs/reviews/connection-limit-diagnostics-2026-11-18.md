# Connection rejection diagnostics

Backend errors retain existing HTTP statuses and error codes, adding a bounded `x-safari-limit-reason` header. Codes distinguish reconnect/concurrent/global socket limits, rejection cooldown, lookup allowance, IP/account request and authentication limits, service burst admission, and known storage-budget failures. Internal admission preserves the code; coordination failure frames preserve storage reasons before closing the socket.

The client maps only known codes to fixed descriptions. Unknown/missing reasons explicitly remain unspecified. Authentication exceptions and WebSocket handshake errors retain the reason. Retry logs include status, safe code, server retry delay and actual backoff; structured retry diagnostics carry the same attribution. Existing one-failure/one-recovery-per-visit chat suppression remains. No raw headers, identities, tokens or server text are displayed. Limits and admission decisions are unchanged.

Older backends/clients remain compatible but cannot display all details. Both backend and client updates must be deployed before the new exact explanations are available; neither was deployed during this work. Storage/provider errors without an attributed code remain generic rather than guessing a quota.

Validation: backend regression suite; client PartyRecoveryRegression reason mapping, unknown metadata sanitization and existing notice suppression checks; full 26.2 check and 26.1.2/26.3 compilation. Logs under local/limit-reasons*.
