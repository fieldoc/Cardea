# Worktree Build & Git

Load when working inside a git worktree (`.claude/worktrees/<name>/`), merging back to main, rebasing, or hitting a build/commit issue specific to the worktree setup.

## Build

- **`local.properties` missing:** worktrees are at `.claude/worktrees/<name>/` — `cp ../../../local.properties .`
- **Windows path-length:** KSP/AAPT fail in deeply-nested worktree build dirs. Copy changed files back to main and build there. Do NOT use `subst` — writes through the mapped drive silently revert edits made via the original path.

## Merging worktree → main

- **Merge worktree into dirty main:** run THREE separate commands (never chain with `&&`): `git stash push -m "..."` → `git merge --ff-only <branch>` → `git stash pop`. If `stash pop` exits 1 on chain, merge never runs.
- **Rewritten-file stash conflicts:** procedure above fails when popped WIP (a) touches a file the branch rewrote, or (b) references untracked files not yet committed. Recovery: `git checkout HEAD -- <file>` → `git reset HEAD` → `git stash drop`. WIP remains as unstaged changes.
- **Stash-pop after ff-merge with duplicate untracked files:** when main mirrors the worktree (e.g. files were copied over for building), `stash push -u` then `merge --ff-only <branch>` then `stash pop` will warn "<file> already exists, no checkout" for the untracked portion — the merge already created them with identical content. Tracked portion (e.g. local `.claude/settings.local.json` edits) pops cleanly; `git stash drop` is safe.
- **Branch diverged behind main:** `--ff-only` fails. Fix: in worktree `git rebase main`, then in main `git merge --ff-only <branch>`. Back-to-back — second main commit forces another rebase. Note: `git fetch . main:main` from the worktree errors ("refusing to fetch into branch checked out at ...") — no need, `git rebase main` reads the local ref directly.
- **`git rebase --continue --no-edit` is invalid** and hard-errors. Use `GIT_EDITOR=true git rebase --continue` for non-interactive continue.
- **Untracked files block `git merge`:** if the incoming branch creates a file present as untracked, `rm` the untracked copy first.

## Worktree teardown

- **`git worktree remove` permission denied:** shell cwd must be outside the worktree. Use `git worktree prune` to clear stale registrations.
