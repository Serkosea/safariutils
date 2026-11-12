# Documentation

- [Handoff](../HANDOFF.md): invariants and remaining live validation.
- [Workspace](development/WORKSPACE.md): source ownership, build matrix and outputs.
- [Settings UI](development/SETTINGS_UI.md): shared controls and layout.
- [Persistence](development/PERSISTENCE.md): settings/history safety.
- [Diagnostics](development/DIAGNOSTICS.md): logging, profiling and replay.
- [Message templates](development/MESSAGE_TEMPLATES.md): supported alert tags.
- [Entity research](development/ENTITY_RESEARCH_FINDINGS.md): evidence constraints.
- [3.8.0 integration update](integrations/PARTY_SYNC_3.8_IMPLEMENTATION.md): run reconciliation, replay and completion evidence.
- [Efficient party transport](reviews/party-sync-efficient-transport.md): transport 3 batching, renewal and completion refreshes.
- [Current peer review](reviews/party-sync-4.3.3-review.md): primary-service compatibility and remaining live checks.
- [Party sync](integrations/PARTY_SYNC.md) and [wire reference](integrations/PARTY_SYNC_REFERENCE.md): client interoperability.
- [History](archive/): dated decisions, not current requirements. Shareable contract reviews live in `reviews/`; raw local research stays ignored.

Runtime packages are grouped by responsibility. Version adapters and regression fixtures remain separate source sets.
