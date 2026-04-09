# GitHub MCP — Usage Rules & Playbook

GitHub MCP tools are prefixed `mcp__github__`. Requires a valid PAT in `.mcp.json` — if tools fail with an auth error, the token placeholder hasn't been replaced yet.

## Setup Reminder

Replace `REPLACE_WITH_YOUR_GITHUB_PAT` in `.mcp.json` with a real token:
- GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
- Required scopes: `repo`, `read:org`, `workflow`

## Core Tools & When to Use Them

| Task | Tool |
|------|------|
| Create a PR after implementing a feature | `create_pull_request` |
| List open PRs | `list_pull_requests` |
| Get PR details / review status | `get_pull_request` |
| Check CI run status | `list_workflow_runs` |
| Get failed job logs | `get_job_logs` |
| Create an issue for a discovered bug | `create_issue` |
| Search issues for prior decisions | `search_issues` |
| Search code across the repo | `search_code` |
| Push a file directly (avoid — prefer local commits) | `create_or_update_file` |

## Standard Delegated-Coding Workflow

When implementing a feature end-to-end:

1. **Implement + test locally** — write code, run `./gradlew test`, fix failures
2. **Commit** — `git commit` with a descriptive message
3. **Push** — `git push` (or `git push -u origin <branch>`)
4. **Create PR** — use `create_pull_request` with:
   - Title: short imperative (e.g. "Add guided workout preset cards")
   - Body: summary of what changed, why, and how to test
   - Base branch: `main` (or whatever the default is)
5. **Monitor CI** — use `list_workflow_runs` to check GitHub Actions status
6. **Report back** — share the PR URL with Graham

## PR Body Template

```
## What
[One-paragraph summary of the change]

## Why
[Link to design doc or feature request]

## How to test
- [ ] Build and install debug APK
- [ ] Navigate to [screen]
- [ ] Verify [specific behaviour]

## Checklist
- [ ] Unit tests pass (`./gradlew test`)
- [ ] No lint warnings (`./gradlew lint`)
```

## Cardea-Specific Notes

- **Default branch:** check `git remote show origin` — likely `main`
- **CI:** check `.github/workflows/` to see what runs on PR (lint, tests, build)
- **Issue labels:** use whatever labels exist in the repo — don't create new ones without asking
- **Draft PRs:** prefer `draft: true` for work-in-progress so Graham can review before it's marked ready

## Decision Rule

Use GitHub MCP for PR/issue lifecycle management. Use local `git` via Bash for commits, branches, and diffs — those don't need the MCP overhead.
