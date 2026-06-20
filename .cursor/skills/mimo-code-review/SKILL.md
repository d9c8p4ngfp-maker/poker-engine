---
name: mimo-code-review
description: Deep code review using subagents with full repo access and scenario-aware review rules from modes/. Use after completing a batch of changes, before committing or creating a PR, or when the user asks for a deep code review. Triggers on keywords like review, 审查, code review, deep review.
disable-model-invocation: false
---

# MiMo Deep Code Review

This skill dispatches a subagent that has **full read access** to the repo — it reads source files, traces callers via codegraph, checks imports and type compatibility, and verifies cross-file consistency. This is a deep review, not a surface diff scan.

## Workflow

### Step 1: Determine what changed

```bash
git diff origin/master...HEAD --name-only
```

### Step 2: Pick review modes

Read `modes/` directory. Match changed files against each mode's "When to Use" triggers. If changes span multiple categories, run the **highest-risk mode** (table ordering is by severity). If unsure which mode applies, run **concurrency** — it's the most critical in this project.

| Mode file | Severity |
|-----------|----------|
| [concurrency](modes/concurrency.md) | Highest |
| [game-logic](modes/game-logic.md) | High |
| [security](modes/security.md) | High |
| [frontend-state](modes/frontend-state.md) | Medium |
| [websocket](modes/websocket.md) | Medium |
| [resource](modes/resource.md) | Medium |

### Step 3: Dispatch subagent

Launch a `general-purpose` subagent with `run_in_background: true`. Give it the selected mode file's **Deep Review Task** section as its prompt, plus the list of changed files. The subagent has full read access — it reads source files, uses codegraph, checks imports, and verifies cross-file consistency.

### Step 4: Act on findings

When the subagent returns, fix P0 issues immediately. Re-run the subagent if changes are substantial.

## Adding new review modes

Create a new `.md` file in `modes/` with this structure:

```markdown
# Mode: {name}

## When to Use
- Modified: {file patterns}
- User mentions: {keywords}

## Known Historical Bugs
- {bug-ID}: {description}

## Deep Review Task
...instructions for the subagent with full repo access...
```

No need to edit SKILL.md — the agent reads `modes/` dynamically when picking modes.
