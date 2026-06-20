# MiMo Code Review Hook (PowerShell)
# Triggered by Cursor "stop" event — reviews all changes made in the session
#
# Prerequisites:
#   1. npm install -g @mimo-ai/cli
#   2. Configure API key in .cursor/hooks/mimo-review.conf
#
# Options (set in mimo-review.conf):
#   MIMO_MODE=api  → call MiMo OpenAI-compatible API directly
#   MIMO_MODE=cli  → use `mimo` CLI (interactive, less suitable for hooks)
param()

$ErrorActionPreference = "Stop"

# ── Config ────────────────────────────────────────────────
$configFile = Join-Path $PSScriptRoot "mimo-review.conf"
$MIMO_MODE     = "api"
$MIMO_API_KEY  = ""
$MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
$MIMO_MODEL    = "mimo-v2.5-pro"

if (Test-Path $configFile) {
    Get-Content $configFile | ForEach-Object {
        if ($_ -match '^\s*MIMO_MODE\s*=\s*(.+)$')      { $MIMO_MODE     = $matches[1].Trim() }
        if ($_ -match '^\s*MIMO_API_KEY\s*=\s*(.+)$')   { $MIMO_API_KEY  = $matches[1].Trim() }
        if ($_ -match '^\s*MIMO_BASE_URL\s*=\s*(.+)$')  { $MIMO_BASE_URL = $matches[1].Trim() }
        if ($_ -match '^\s*MIMO_MODEL\s*=\s*(.+)$')     { $MIMO_MODEL    = $matches[1].Trim() }
    }
}

if ($MIMO_MODE -eq "api" -and (-not $MIMO_API_KEY -or $MIMO_API_KEY -eq "sk-your-key-here")) {
    Write-Output '{"followup_message": "MiMo review skipped: API key not configured. Copy .cursor/hooks/mimo-review.conf.template to mimo-review.conf and set MIMO_API_KEY."}'
    exit 0
}

# ── Read hook input from stdin ─────────────────────────────
try {
    $rawInput = [Console]::In.ReadToEnd()
    if ($rawInput) {
        $hookData = $rawInput | ConvertFrom-Json
    } else {
        $hookData = $null
    }
} catch {
    $hookData = $null
}

# ── Collect diff ───────────────────────────────────────────
$repoRoot = git rev-parse --show-toplevel 2>$null
if (-not $repoRoot) {
    Write-Output '{"followup_message": "MiMo review skipped: not in a git repository."}'
    exit 0
}

Push-Location $repoRoot

# Collect all current changes (unstaged + staged)
$diff = (& git diff -- . ':!*.lock' ':!package-lock.json' ':!*.png' ':!*.jpg' ':!*.svg' 2>&1 | Out-String)
$diff += (& git diff --cached -- . ':!*.lock' ':!package-lock.json' ':!*.png' ':!*.jpg' ':!*.svg' 2>&1 | Out-String)

# Include untracked text files
$untracked = & git ls-files --others --exclude-standard -- '*.ts' '*.tsx' '*.vue' '*.css' '*.html' '*.java' '*.md' '*.json' '*.properties' '*.xml' 2>&1
foreach ($f in $untracked) {
    $filePath = Join-Path $repoRoot $f
    if ($f -and (Test-Path $filePath -PathType Leaf)) {
        $diff += "`n`n--- NEW FILE: $f ---`n"
        $diff += Get-Content $filePath -Raw -ErrorAction SilentlyContinue
    }
}

Pop-Location

if (-not $diff -or $diff.Trim().Length -lt 10) {
    Write-Output '{"followup_message": "MiMo review skipped: no code changes detected in this session."}'
    exit 0
}

# ── Truncate if too large ──────────────────────────────────
$MAX_CHARS = 50000
if ($diff.Length -gt $MAX_CHARS) {
    $diff = $diff.Substring(0, $MAX_CHARS) + "`n`n[... truncated, diff too large ...]"
}

# ── Review prompt ──────────────────────────────────────────
$prompt = @"
You are a senior code reviewer. Review the following git diff and provide structured feedback.

## Instructions
1. Identify bugs, logic errors, security issues (P0)
2. Flag code smells, poor naming, missing tests (P1)
3. Suggest improvements for readability and performance (P2)
4. Keep feedback concise — use bullet points
5. If the code looks good, say so briefly

## Output Format
- **P0 Critical**: [issues that must be fixed]
- **P1 Warning**: [issues that should be addressed]
- **P2 Suggestions**: [optional improvements]
- **Overall**: [one-sentence verdict]

## Diff to review
```diff
$diff
```
"@

# ── Call MiMo API ──────────────────────────────────────────
$body = @{
    model    = $MIMO_MODEL
    messages = @(
        @{ role = "user"; content = $prompt }
    )
    temperature = 0.3
    max_tokens  = 2000
} | ConvertTo-Json -Depth 5

try {
    $response = Invoke-RestMethod -Uri "$MIMO_BASE_URL/chat/completions" `
        -Method Post `
        -Headers @{
            "Authorization" = "Bearer $MIMO_API_KEY"
            "Content-Type"  = "application/json"
        } `
        -Body $body `
        -TimeoutSec 90

    $review = $response.choices[0].message.content

    # Save to file for reference
    $reviewDir = Join-Path $repoRoot ".cursor/reviews"
    New-Item -ItemType Directory -Force -Path $reviewDir | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $review | Out-File -FilePath (Join-Path $reviewDir "review-$timestamp.md") -Encoding utf8

    # Return followup_message for next session
    $summary = if ($review.Length -gt 300) { $review.Substring(0, 300) + "..." } else { $review }
    $result = @{
        followup_message = "MiMo review completed:`n$summary`n`nFull review saved to .cursor/reviews/review-$timestamp.md"
    } | ConvertTo-Json -Compress
    Write-Output $result
} catch {
    $result = @{
        followup_message = "MiMo review failed: $($_.Exception.Message)"
    } | ConvertTo-Json -Compress
    Write-Output $result
}
