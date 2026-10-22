## Changes & Additions

- Improved API compatibility: player lookups, party-shared Sparklings and party sync no longer require Minecraft chat-signing profile keys
- Centralized Bazaar caching and retry handling to reduce duplicate requests and background work
- Added shared, bounded official auction-price snapshot support; missing or expired prices remain unavailable rather than being guessed

## API Compatibility

Update to this version if lookups previously failed with a Minecraft profile authentication error. No API key or additional setup is needed. Service sessions use a supplied UUID for usage accounting, not verified account ownership; independent global request and connection limits help protect shared resources. Network errors and service limits can still occur.

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-3.1.3+mc26.1.2.jar`
- `safariutils-3.1.3-extra+mc26.1.2.jar`
- `safariutils-3.1.3+mc26.2.jar`
- `safariutils-3.1.3-extra+mc26.2.jar`

The regular jars use Safe Mode; Extra includes toggles in the Safe Mode category that when turned off, provide information the player cannot directly see and may not be safe to use; Minecraft 26.1.2 builds have received limited testing compared to 26.2.
