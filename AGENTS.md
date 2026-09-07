# Contribution instructions

- Treat this repository, its pull requests, Actions logs, artifacts, and releases as public material.
- Never commit private project notes, conversation links, personal examples, credentials, tokens, private player payloads, database exports, or internal operational details.
- Update the separately maintained private project handoff alongside each change. Do not name or link that private location here.
- Use a focused branch and detailed pull request for every change. Record public-safe validation and release results in the pull request.
- Preserve unrelated work and clearly distinguish website deployment from an installed plugin build or release.
- Plugin behavior must comply with the current Jagex Third-Party Client Guidelines and RuneLite Plugin Hub requirements.
- Keep collection passive and consent-based. Do not automate gameplay or navigation, expose hidden information, provide prohibited combat assistance, modify protected click zones, or collect Jagex credentials.
- If compliance is unclear, pause that feature and seek clarification instead of claiming official approval.
- Maintain the manual verification checklist in README.md with each shipped behavior change. Add stable test IDs, concrete steps, expected results, and version requirements. Keep unconfirmed items unchecked; CI does not constitute manual confirmation. Mark an item complete only after the maintainer reports it working, recording the date/version when known. Keep unfinished features out of the released testing list.
