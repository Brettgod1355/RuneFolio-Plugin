# Discord notification delivery

Pull requests merged into main publish only their explicit `## Discord changelog` section. Use 20–1300 characters of public, plain-language notes describing changes and any required user action. No URLs, mentions, code fences, private details, or test logs. The bot appends the PR link and distinguishes merged code from deployed/installed updates. Missing notes produce a private owner alert; update the PR body and manually dispatch `Discord notifications` with the merged PR number to deliver it.

Configure repository variable `DISCORD_EVENTS_URL` with the HTTPS manager origin and secret `DISCORD_EVENTS_SECRET` with the separate source HMAC key. The manager must have the matching source secret and repository allowlist. Without the URL the workflow is skipped; missing credentials cannot deliver messages. The manager's local integration setup script can provision these settings securely. Never commit keys or put them in PR bodies.

The workflow uses only main code, never a PR head, with read-only GitHub permissions. Only merged main PRs and failed main CI/Plugin Hub preflight runs are reported. Manual dispatch with no PR number sends a private test. Public summaries disable all notification mentions; operational errors can mention only the configured owner in a verified private channel.

Signed requests include a timestamp and bounded JSON. No raw workflow logs or entire PR bodies are posted. If delivery is uncertain, inspect Discord before rerunning; the receiver intentionally does not blindly repeat an ambiguous send. Links into private repositories still require repository access.
