DONE: basic-invocation
DONE: prompt-flag
DONE: yolo-flag
DONE: model-flag
DONE: file-context-flags
DONE: all-files-flag
DONE: mcp-support
DONE: tool-use
DONE: checkpoint-flag
DONE: sandbox-mode
DONE: debug-mode
DONE: non-interactive-patterns
DONE: auth-setup
DONE: gemini-self-describe-gaps
---
## basic-invocation
The Gemini CLI (v0.32.1) is invoked using the `gemini` command.
### Basic Syntax
```bash
gemini [options] [prompt]
# OR
gemini <subcommand> [arguments]
```
### Key Subcommands
- **`config` / `configure`**: Manage settings (API keys, default model).
- **`models`**: List all available Gemini models.
- **`help`**: Display help for the CLI or a specific subcommand.
- **`chat`**: Start an interactive session explicitly.
### Invocation Examples
Running with **no arguments** starts an interactive REPL session.
Running with **a prompt argument** executes one-shot and exits.
```bash
gemini                          # interactive REPL
gemini "Explain Val vs Var"     # one-shot
gemini models                   # list models
gemini config set model gemini-1.5-pro
```
---
## prompt-flag
The `-p` / `--prompt` flag explicitly marks the prompt string, preferred for complex queries.
### `-p` vs Positional Arguments
- **Positional**: text concatenated — convenient but risky with shell special chars (`;`, `&`, `|`)
- **`-p` flag**: clearly delimits input from other flags — preferred for multi-line or special-char prompts
### Stdin and File Support
The CLI is pipe-friendly. Piped text is ingested as context; `-p` provides the instruction.
```bash
# Basic use
gemini -p "Explain StateFlow vs Flow in Kotlin."
# Pipe git diff for commit message suggestion
git diff HEAD | gemini -p "Summarize these changes and suggest a commit message."
# Pipe a log file to find errors
cat app_logs.txt | gemini -p "Find the root cause of the crash in these logs."
# File redirection
gemini < bug_report.md -p "Create a reproduction script based on this report."
# Positional (simpler, less robust)
gemini what is the current version of the android gradle plugin
```

## yolo-flag

}
Error: The operation was aborted.
Error: The operation was aborted.
The `--yolo` (or `-y`) flag in the Gemini CLI enables **YOLO mode**, which grants the AI agent full autonomy to execute actions without manual intervention.

### What it Does
It bypasses the CLI's standard **approval loop**. Normally, the agent must prompt you for permission before performing sensitive operations like executing shell commands, modifying files, or using MCP (Model Context Protocol) tools. In YOLO mode, these actions are performed automatically.

### Prompts and Confirmations Skipped
*   **Shell Command Execution:** Skips the confirmation for running scripts, builds, or system-level commands.
*   **File Modifications:** Automatically applies edits or overwrites files without showing a diff or asking for approval.
*   **Tool Usage:** Grants immediate access to all available external tools and integrations.

### When to Use It
*   **Automated CI/CD Pipelines:** When running the CLI in a non-interactive environment where no human is present to provide input.
*   **Repetitive Batch Tasks:** For large-scale refactors (e.g., fixing linting errors or adding license headers across 100+ files) where clicking "Allow" for every file would be impractical.
*   **Trusted, Sandbox Environments:** In local projects where you are confident in the agent's logic and the potential risk of a bad command is minimal.

**Note:** In recent versions of the CLI, the standalone `--yolo` flag is being deprecated in favor of the more explicit `--approval-mode=yolo`. Use it with extreme caution, as the agent can inadvertently delete data or execute harmful code if it misinterprets a task in an untrusted codebase.
the agent uses tools to interact with external services (like Google Workspace or Auth0), those actions are also auto-approved.
*   **Safety Checks:** The primary "Human-in-the-loop" safety check is disabled. The agent assumes it has full trust to proceed with its plan.

### **When to use it vs. when to avoid it**
| **Scenario** | **Use YOLO Mode?** | **Reasoning** |
| :--- | :--- | :--- |
| **Sandboxed/Disposable Environments** | ✅ **Yes** | If you're testing in a Docker container or a fresh VM where data loss is irrelevant. |
| **Repetitive, Low-Risk Tasks** | ✅ **Yes** | For tasks like "add a license header to these 50 files" where you trust the pattern. |
| **Bootstrapping a New Project** | ✅ **Yes** | Speeding up initial scaffolding and setup. |
| **Production or Sensitive Code** | ❌ **No** | One hallucinated shell command (like an accidental deletion) can be catastrophic. |
| **Complex System Refactoring** | ❌ **No** | You want to review the agent's logic at each step to ensure it doesn't break dependencies. |
| **Untrusted or Large Codebases** | ❌ **No** | Without confirmation, you lose the ability to catch the agent if it starts scanning sensitive files. |

### **Examples**

**1. Standard Mode (Default)**
```bash
gemini "Refactor the login logic in AuthController.kt"
# [Gemini CLI]: I will now update AuthController.kt.
# [?] Execute write_file for AuthController.kt? (Y/n)
```

**2. YOLO Mode**
```bash
gemini --yolo "Update all copyright headers to 2026"
# [Gemini CLI]: I will now update headers in 45 files.
# (Actions proceed automatically without stopping for confirmation)
```

**3. The "Easter Egg"**
If you run `gemini --yolo --help`, the documentation often includes a "tutorial video" link. In a nod to the risky nature of the flag, the link is a **Rickroll**, reminding you that "you know the rules, and so do I" (but the rules are now gone).

## model-flag

### `-m` / `--model` flag

Specifies which Gemini model to use for the current session. Type: `string`.

```bash
gemini -m gemini-2.5-pro -p "Explain coroutines in Kotlin"
gemini --model gemini-1.5-flash "Quick summary of this file"
```

### Setting a persistent default

Use `gemini config` to set the model that applies when `-m` is not provided:

```bash
gemini config set model gemini-2.5-pro
```

### Listing available models

```bash
gemini models
```

### Priority: per-invocation `-m` overrides the configured default.


## file-context-flags

### `--include-directories`

Adds extra directories to the workspace context beyond the current directory.
Type: `array` — comma-separated or multiple flags.

```bash
gemini --include-directories ../shared-lib,../common -p "How does authentication flow across these modules?"
gemini --include-directories /path/to/api-specs --include-directories /path/to/sdk -p "Generate a client"
```

### `@file` inline file injection

Prefix a file path with `@` to inject its content directly into the prompt:

```bash
gemini -p "Review this code: @src/main/kotlin/Foo.kt"
gemini -p "Summarize: @README.md and suggest improvements"
```

### Stdin piping (also a form of file context)

```bash
cat bigfile.txt | gemini -p "Summarize this"
git diff | gemini -p "Write a commit message for these changes"
```


## all-files-flag

There is no single `--all-files` flag in Gemini CLI. Workspace file access works as follows:

### Default workspace
The CLI automatically includes the **current working directory** as the workspace. Gemini can read and edit files in this directory using its built-in tools (`read_file`, `write_file`, `list_directory`, etc.).

### Extending the workspace
Use `--include-directories` to add extra directories:

```bash
gemini --include-directories ../lib,../shared -p "Refactor the auth module"
```

### Selective file injection via `@`
Reference specific files inline with `@`:

```bash
gemini -p "Review @src/main/kotlin/MyViewModel.kt and @build.gradle.kts"
```

### GEMINI.md
Place a `GEMINI.md` file in your project root to give Gemini persistent project context (equivalent to Claude's CLAUDE.md). It is automatically loaded at startup.


## mcp-support

Gemini CLI has built-in MCP (Model Context Protocol) support for extending the agent with external tools and servers.

### `gemini mcp` subcommand

```
gemini mcp add <name> <commandOrUrl> [args...]   Add a server
gemini mcp remove <name>                          Remove a server
gemini mcp list                                   List all configured MCP servers
gemini mcp enable <name>                          Enable an MCP server
gemini mcp disable <name>                         Disable an MCP server
```

### Adding an MCP server (stdio)

```bash
gemini mcp add mytools python /path/to/mcp_server.py
gemini mcp add filesystem npx @modelcontextprotocol/server-filesystem /workspace
```

### Adding an MCP server (HTTP/SSE)

```bash
gemini mcp add remote-api https://api.example.com/mcp
```

### Restricting which servers to load

The `--allowed-mcp-server-names` flag limits which configured servers are loaded for a session:

```bash
gemini --allowed-mcp-server-names serena,mytools -p "Analyze the codebase"
```

### Example `mcp list` output

```
✓ serena: serena start-mcp-server --project-from-cwd  (stdio) - Connected
✓ adbServer: node mcp-server/dist/index.js (stdio) - Connected
✗ auth0: npx @auth0/auth0-mcp-server run (stdio) - Disconnected
```

### Config file location
MCP servers are stored in `~/.gemini/settings.json` (global) or `.gemini/settings.json` (project-level).


## tool-use

}

### Core File & Search Tools
*   **`list_directory`**: Lists the names of files and subdirectories within a specified path.
*   **`read_file`**: Reads and returns the content (text, image, audio, or PDF) of a specified file.
*   **`grep_search`**: Performs high-speed regular expression searches within file contents.
*   **`glob`**: Efficiently finds files matching specific patterns (e.g., `src/**/*.ts`).
*   **`execute_shell_command`**: Executes a shell command and returns its stdout and stderr.

### Symbolic Code Analysis (Serena)
*   **`get_symbols_overview`**: Provides a high-level JSON summary of code symbols (classes, methods) in a file.
*   **`find_symbol`**: Locates and retrieves metadata or source code for specific code entities.
*   **`find_referencing_symbols`**: Finds all locations in the codebase that reference a specific symbol.
*   **`replace_symbol_body`**: Replaces the implementation of a specific code symbol (function, class, etc.).
*   **`insert_after_symbol`**: Inserts new code immediately following a specific symbol's definition.
*   **`insert_before_symbol`**: Inserts code or imports immediately before a specific symbol.
*   **`rename_symbol`**: Performs a project-wide rename of a symbol across all files.
*   **`search_for_pattern`**: Executes flexible pattern matching across code and non-code files.

### Specialized Sub-Agents & Skills
*   **`codebase_investigator`**: Deploys a sub-agent for deep architectural analysis and bug root-cause investigation.
*   **`generalist`**: Invokes a general-purpose agent for high-volume batch tasks or repetitive refactoring.
*   **`cli_help`**: Answers questions regarding Gemini CLI features, documentation, and configuration.
*   **`activate_skill`**: Enables specialized "recipes" or "personas" for specific workflows (e.g., Google Workspace integration).

### Android ADB Control
*   **`adb_devices`**: Lists all connected Android devices and emulators.
*   **`get_screen_summary`**: Retrieves a token-efficient summary of the current interactive UI elements.
*   **`get_screen`**: Captures the full screen state of the Android device as a JSON object.
*   **`inspect_ui`**: Dumps the complete XML UI hierarchy for precise element identification.
*   **`execute_action`**: Performs a single tap, type, or key event on the connected device.
*   **`execute_batch`**: Runs a sequence of ADB actions in a single efficient turn.
*   **`run_ai_script`**: Executes a Python script for complex, multi-step ADB automation logic.
*   **`adb_logcat`**: Streams system and application logs for debugging Android behavior.
*   **`check_env`**: Verifies the ADB environment and device connectivity status.

### Project & Memory Management
*   **`activate_project`**: Switches the agent's focus to a specific directory or registered project.
*   **`get_current_config`**: Displays active projects, tools, modes, and environment configuration.
*   **`save_memory`**: Persists global user preferences or facts across all future sessions.
*   **`write_memory`**: Records project-specific insights into organized Markdown memory files.
*   **`read_memory`**: Retrieves previously stored information from project or global memories.
*   **`list_memories`**: Lists available memory files, optionally filtered by topic.
*   **`switch_modes`**: Adjusts operational modes like "editing," "interactive," or "planning."

### Information & Utility
*   **`google_web_search`**: Performs grounded web searches for technical documentation or troubleshooting.
*   **`initial_instructions`**: Accesses the 'Serena Instructions Manual' for tool usage best practices.
*   **`onboarding`**: Generates a guide for setting up a new project context for the agent.

## checkpoint-flag

Gemini CLI supports session persistence — saving and resuming multi-turn conversations.

### Session flags (from `--help`)

| Flag | Description |
|------|-------------|
| `-r` / `--resume` | Resume a previous session. Use `"latest"` or an index number. |
| `--list-sessions` | List available sessions for the current project and exit. |
| `--delete-session` | Delete a session by index number. |

### Usage examples

```bash
# List all saved sessions for the current project
gemini --list-sessions

# Resume the most recent session
gemini --resume latest

# Resume session #3
gemini --resume 3

# Delete session #2
gemini --delete-session 2
```

### Notes
- Sessions are scoped to the **current project directory**.
- Sessions store the full conversation context, allowing you to pick up exactly where you left off.
- There is no explicit `--checkpoint` flag; `--resume` is the mechanism for session continuity.


## sandbox-mode

### `-s` / `--sandbox` flag

Runs Gemini CLI in a sandboxed environment. Type: `boolean`.

```bash
gemini --sandbox -p "Refactor and test this code safely"
gemini -s "Run these shell commands in isolation"
```

### What sandbox mode does
- Executes shell commands and file operations in an **isolated environment** (container or restricted shell) to prevent unintended changes to the host system.
- Ideal for running untrusted prompts or experimenting with destructive operations.
- Can be combined with `--yolo` for fully automated sandboxed execution.

### Approval mode: `plan`
An alternative read-only mode is `--approval-mode plan`, which restricts the agent to **read-only** operations only (no writes, no shell execution):

```bash
gemini --approval-mode plan -p "Analyze this codebase and tell me what needs to change"
```

### Approval mode options (from `--help`)
| Mode | Behavior |
|------|----------|
| `default` | Prompt for approval before each tool action |
| `auto_edit` | Auto-approve edit tools (file writes), ask for others |
| `yolo` | Auto-approve all tools (same as `--yolo`) |
| `plan` | Read-only mode — no writes or shell execution |


## debug-mode

### `-d` / `--debug` flag

Runs Gemini CLI in debug mode. Type: `boolean`. Default: `false`.

```bash
gemini --debug -p "Why is my code failing?"
gemini -d "Analyze this build error"
```

### What debug mode does
- Opens the **debug console** accessible via `F12` (similar to browser DevTools).
- Shows internal agent reasoning, tool call traces, and API request/response details.
- Useful for diagnosing why the agent is behaving unexpectedly or which tools it's calling.

### Output formats (related: `-o` / `--output-format`)
Separate from debug mode, you can change the output format for scripting:

| Format | Description |
|--------|-------------|
| `text` | Default human-readable output |
| `json` | Full structured JSON output |
| `stream-json` | Streaming JSON (NDJSON) for real-time processing |

```bash
gemini -o json -p "List files in src/" | jq '.response'
gemini --output-format stream-json -p "Generate a long report" | while read line; do echo "$line" | jq '.chunk'; done
```

### Raw output flag
`--raw-output` disables sanitization of model output (allows ANSI escape sequences). Use `--accept-raw-output-risk` to suppress the security warning.


## non-interactive-patterns

Gemini CLI is fully scriptable. Key flags and patterns for non-interactive (headless) use:

### Core flag: `-p` / `--prompt`
Triggers non-interactive mode. Response is printed to stdout and the process exits.

```bash
# One-shot query
gemini -p "What is the Big O complexity of merge sort?"

# Capture output to file
gemini -p "Generate a Kotlin data class for a User with name, email, age" > User.kt

# Use in a script
SUMMARY=$(gemini -p "Summarize: @README.md")
echo "$SUMMARY"
```

### Interactive-then-exit: `-i` / `--prompt-interactive`
Executes the provided prompt and then **continues in interactive mode**:

```bash
gemini -i "Load this project context: @CLAUDE.md"
# Drops into REPL with context already loaded
```

### Piping patterns

```bash
# Analyze git diff
git diff HEAD | gemini -p "Write a conventional commit message for these changes"

# Review a file
cat src/main/kotlin/MyClass.kt | gemini -p "Find bugs and suggest fixes"

# Process build output
./gradlew test 2>&1 | gemini -p "Summarize test failures and suggest fixes"

# Chain with jq for structured output
gemini -o json -p "List 5 Kotlin best practices" | jq '.response'
```

### CI/CD usage pattern

```bash
# In a GitHub Actions step or CI script:
gemini -y -p "Fix all lint warnings in @src/main/kotlin/..." \
  --model gemini-2.5-pro \
  --output-format text
```

### Output format for scripting

```bash
gemini -o json -p "..."        # structured JSON
gemini -o stream-json -p "..."  # NDJSON for streaming
gemini -o text -p "..."         # plain text (default)
```


## auth-setup

### Authentication methods
Gemini CLI supports multiple auth strategies, selected during first run or via `gemini config`:

| Method | Description |
|--------|-------------|
| `oauth-personal` | Google personal account OAuth (browser-based login) — most common |
| `oauth-workspace` | Google Workspace account OAuth |
| `api-key` | Static Gemini API key from Google AI Studio |
| `vertex-ai` | Google Cloud Vertex AI credentials (for enterprise/GCP use) |

### First-time setup
Simply run `gemini` — it will prompt you to authenticate via browser OAuth. Credentials are cached in `~/.gemini/oauth_creds.json`.

### Config file locations
| File | Purpose |
|------|---------|
| `~/.gemini/settings.json` | Global settings (auth type, session retention, default model) |
| `~/.gemini/oauth_creds.json` | Cached OAuth credentials |
| `~/.gemini/google_accounts.json` | Linked Google accounts |
| `~/.gemini/trustedFolders.json` | Folders allowed to run Gemini without confirmation |
| `.gemini/settings.json` | Project-level overrides (checked into repo) |
| `GEMINI.md` | Project context file (auto-loaded at startup) |

### Example `~/.gemini/settings.json`

```json
{
  "security": {
    "auth": {
      "selectedType": "oauth-personal"
    }
  },
  "general": {
    "sessionRetention": {
      "enabled": true,
      "maxAge": "30d"
    }
  }
}
```

### API key alternative
```bash
GEMINI_API_KEY=your-key-here gemini -p "Hello"
# or set permanently:
gemini config set apiKey your-key-here
```

DONE: config-subcommand
DONE: system-instruction-flag
DONE: generation-parameter-flags
DONE: no-color-flag
DONE: slash-commands-interactive
DONE: context-files-geminimd

## gemini-self-describe-gaps
Gemini identified these additional undocumented CLI topics (added as TODOs above):
- config-subcommand: `gemini config` depth (get/set/list)
- system-instruction-flag: `--system-instruction` / `-si` for custom system prompt
- generation-parameter-flags: `--temperature`, `--top-p`, `--max-output-tokens`
- no-color-flag: `--no-color` for CI/log-friendly output
- slash-commands-interactive: in-session `/help`, `/bug`, `/mcp` etc.
- context-files-geminimd: `GEMINI.md` project context file in depth

## config-subcommand
There is no `gemini config` CLI subcommand. Configuration is managed via JSON settings files.

### Settings file locations
| File | Scope |
|------|-------|
| `~/.gemini/settings.json` | Global (all projects) |
| `.gemini/settings.json` | Project-level (overrides global) |

### Example `~/.gemini/settings.json`
```json
{
  "security": {
    "auth": {
      "selectedType": "oauth-personal"
    }
  },
  "general": {
    "sessionRetention": {
      "enabled": true,
      "maxAge": "30d"
    }
  }
}
```

### Trusted folders
`~/.gemini/trustedFolders.json` — folders where Gemini runs without additional confirmations.

### Changing the default model
There is no `gemini config set model` command. Set `model` directly in `settings.json` or pass `-m` per invocation:
```bash
gemini -m gemini-2.5-pro -p "..."
```

## system-instruction-flag
There is no `--system-instruction` flag in the current Gemini CLI. The equivalent mechanisms are:

### `--policy` flag
Loads additional policy files or directories that constrain agent behavior:
```bash
gemini --policy ./my-policy.md -p "Refactor this code"
gemini --policy /path/to/policies/ -p "..."
```
Comma-separated or multiple `--policy` flags accepted.

### `GEMINI.md` (project context)
Place a `GEMINI.md` in your project root to give the agent persistent instructions. It is auto-loaded on startup — this is the primary way to set system-level context.

### `--approval-mode` flag
Controls agent autonomy level — an alternative to direct system instruction:
```bash
gemini --approval-mode plan   # read-only, no writes
gemini --approval-mode auto_edit  # auto-approve file edits
gemini --approval-mode yolo   # auto-approve everything
gemini --approval-mode default  # prompt for each action
```

## generation-parameter-flags
The current Gemini CLI does **not** expose `--temperature`, `--top-p`, or `--max-output-tokens` as CLI flags. Model generation parameters are not user-configurable at the CLI level.

### What IS configurable at invocation time
| Flag | Description |
|------|-------------|
| `-m` / `--model` | Select model (affects capability and speed) |
| `-o` / `--output-format` | `text` (default), `json`, or `stream-json` |
| `--raw-output` | Disable output sanitization (allow ANSI sequences) |
| `--accept-raw-output-risk` | Suppress security warning for `--raw-output` |
| `--approval-mode` | `default`, `auto_edit`, `yolo`, or `plan` |

### Output format examples
```bash
gemini -o json -p "List top 5 Kotlin best practices"
gemini -o stream-json -p "Generate a long report"  # NDJSON streaming
gemini -o text -p "Explain coroutines"             # default
```

## no-color-flag
There is no `--no-color` flag in the current Gemini CLI. For plain output in CI environments, use:

### `--output-format json` or `stream-json`
Structured output with no color codes:
```bash
gemini -o json -p "..." | jq '.response'
```

### `--raw-output`
Passes through the model's raw output (including any ANSI sequences it generates). Combine with `--accept-raw-output-risk` to suppress the security warning:
```bash
gemini --raw-output --accept-raw-output-risk -p "..."
```

### Screen reader mode
`--screen-reader` enables accessibility mode which also reduces decorative formatting:
```bash
gemini --screen-reader -p "..."
```

## slash-commands-interactive
Slash commands are available inside a Gemini CLI interactive (REPL) session. Type `/` to see completions.

### Core slash commands

| Command | Description |
|---------|-------------|
| `/help` | Show help for available slash commands |
| `/quit` or `/exit` | Exit the interactive session |
| `/clear` | Clear the conversation history |
| `/mcp` | List MCP server status and available tools |
| `/tools` | List all tools available to the agent |
| `/bug` | Report a bug or issue with the Gemini CLI |
| `/memory` | Show or manage persistent memory |
| `/skills` | List enabled agent skills |
| `/extensions` | List loaded extensions |
| `/hooks` | List configured hooks |
| `/stats` | Show session stats (tokens used, etc.) |
| `/compress` | Compress conversation history to save context |
| `/restore` | Restore a previously compressed conversation |

### Usage
```
> /help
> /mcp
> /tools
```
These commands only work inside an interactive session — they are not available in `-p` (headless) mode.

### `hooks migrate` subcommand
For users migrating from Claude Code:
```bash
gemini hooks migrate
```
Migrates Claude Code hooks configuration to Gemini CLI format.

## context-files-geminimd
`GEMINI.md` is the Gemini CLI equivalent of Claude Code's `CLAUDE.md`. It provides persistent, project-scoped context to the agent.

### How it works
- Place `GEMINI.md` in your project root (or any parent directory)
- It is **automatically loaded** on every Gemini CLI session in that directory
- Multiple `GEMINI.md` files are loaded hierarchically (parent → child)
- Use it for: project architecture, coding conventions, build commands, important file paths

### Example `GEMINI.md`
```markdown
# Project Context

## Build Commands
- Build: `./gradlew :app:assembleDebug`
- Test: `./gradlew :app:testDebugUnitTest`

## Architecture
MVVM + Hilt DI. Key files:
- `HomeViewModel.kt` — home screen state
- `BootcampRepository.kt` — training data

## Conventions
- Use Kotlin coroutines, not RxJava
- Room DAOs are suspend functions
```

### Extensions system
`~/.gemini/extensions/` — directory for installed extensions. Each extension has a `gemini-extension.json` manifest.

```bash
gemini extensions list
gemini extensions install https://github.com/example/my-extension
gemini extensions install ./local-extension/
gemini extensions update --all
gemini extensions disable my-extension
gemini extensions enable my-extension
gemini extensions new ./my-new-extension boilerplate
gemini extensions validate ./my-extension/
```

### Skills system
Agent skills are specialized capabilities (like superpowers):
```bash
gemini skills list
gemini skills list --all
gemini skills install https://github.com/example/skill
gemini skills install ./local-skill/ --scope project
gemini skills enable my-skill
gemini skills disable my-skill
gemini skills uninstall my-skill
gemini skills link ./dev-skill/   # live-linked local development
```
