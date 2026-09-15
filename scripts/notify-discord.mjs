import { createHmac, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function publicSummary(body) {
  const match = String(body || '').match(/^## Discord changelog\s*\n([\s\S]*?)(?=^## |$(?![\s\S]))/m);
  const text = match?.[1].replace(/<!--[\s\S]*?-->/g, '').trim();
  if (!text || text.length < 20 || text.length > 1300 || /@|<|>|https?:|```|\[ ?\]|TODO|write .*summary|replace .*text/i.test(text)) return null;
  return text;
}
export function notification(event, source, repo, runId) {
  if (!['website', 'plugin'].includes(source) || !/^[\w.-]+\/[\w.-]+$/.test(repo)) throw new Error('Invalid notification configuration');
  const pr = event.pull_request;
  if (pr) {
    if (!pr.merged || pr.base?.ref !== 'main' || pr.base?.repo?.full_name !== repo) return null;
    const summary = publicSummary(pr.body);
    const title = String(pr.title || '').replace(/^(feat|fix|chore|docs|test|ci|ui|perf|refactor)(\([^)]*\))?:\s*/i, '').trim();
    if (!summary || !title || title.length > 180 || /@|<|>|https?:|```/.test(title)) return { id: randomUUID(), kind: 'error', code: 'missing_changelog', number: pr.number, url: `https://github.com/${repo}/pull/${pr.number}` };
    return { id: randomUUID(), kind: 'merge', number: pr.number, sha: pr.merge_commit_sha, title, summary, url: `https://github.com/${repo}/pull/${pr.number}` };
  }
  const run = event.workflow_run;
  if (run && ['failure','timed_out','action_required'].includes(run.conclusion) && run.head_branch === 'main' && run.head_repository?.full_name === repo) return { id: randomUUID(), kind: 'error', code: 'ci_failed', run: run.id, url: `https://github.com/${repo}/actions/runs/${run.id}` };
  if (event.test === true) return { id: randomUUID(), kind: 'error', code: 'test', run: runId, url: `https://github.com/${repo}/actions/runs/${runId}` };
  return null;
}
async function main() {
  const source = process.env.DISCORD_SOURCE, repo = process.env.GITHUB_REPOSITORY;
  let event = JSON.parse(await readFile(process.env.GITHUB_EVENT_PATH, 'utf8'));
  if (process.env.GITHUB_EVENT_NAME === 'workflow_dispatch') {
    if (event.inputs?.pr_number) {
      const number = String(event.inputs.pr_number);
      if (!/^\d{1,9}$/.test(number)) throw new Error('Invalid PR number');
      const response = await fetch(`https://api.github.com/repos/${repo}/pulls/${number}`, { headers: { Authorization: `Bearer ${process.env.GITHUB_TOKEN}`, Accept: 'application/vnd.github+json' }, redirect: 'error', signal: AbortSignal.timeout(10000) });
      if (!response.ok) throw new Error('Could not read requested PR');
      event = { pull_request: await response.json() };
    } else event = { test: true };
  }
  const payload = notification(event, source, repo, process.env.GITHUB_RUN_ID);
  if (!payload) { console.log('No notification needed for this event.'); return; }
  const secret = process.env.DISCORD_EVENTS_SECRET;
  if (!/^[a-f0-9]{64}$/.test(secret || '') || !process.env.DISCORD_EVENTS_URL) throw new Error('Discord integration settings are missing. Run the secure integration setup.');
  const base = new URL(process.env.DISCORD_EVENTS_URL);
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash || base.pathname !== '/') throw new Error('Invalid Discord endpoint');
  const raw = JSON.stringify(payload), timestamp = String(Date.now());
  const signature = createHmac('sha256', secret).update(timestamp + '.' + raw).digest('hex');
  const response = await fetch(new URL(`/events/${source}`, base), { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000), headers: { 'Content-Type': 'application/json', 'x-rf-timestamp': timestamp, 'x-rf-signature': signature }, body: raw });
  if (!response.ok) throw new Error('Discord delivery failed. Inspect the channel before retrying.');
  const result = await response.json();
  if (!['sent','grouped','duplicate_or_uncertain'].includes(result.status)) throw new Error('Unexpected delivery result');
  console.log('Discord delivery: ' + result.status);
  if (payload.code === 'missing_changelog') throw new Error('Add a public ## Discord changelog section to the merged PR, then manually dispatch this workflow with its PR number.');
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(['Discord integration settings are missing. Run the secure integration setup.', 'Add a public ## Discord changelog section to the merged PR, then manually dispatch this workflow with its PR number.'].includes(error.message) ? error.message : 'Notification did not complete. Check integration settings and inspect Discord before retrying.'); process.exitCode = 1; });
}
