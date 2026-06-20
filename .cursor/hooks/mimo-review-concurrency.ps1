$apiKey = "sk-csnvdf0u6rgk0gj4az4q9mn3n2oa67b8890xxxi5p4oywe60"
$baseUrl = "https://api.xiaomimimo.com/v1"
$model = "mimo-v2.5-pro"

Push-Location D:\poker-web
$diff = (& git diff origin/master...HEAD -- . ':!*.lock' ':!package-lock.json' ':!*.png' ':!*.jpg' ':!*.svg' 2>&1 | Out-String)
if ($diff.Length -gt 40000) { $diff = $diff.Substring(0, 40000) + [Environment]::NewLine + "[... truncated ...]" }
Pop-Location

$promptLines = @()
$promptLines += "You are a senior concurrency reviewer for a Spring Boot + ReentrantLock game server."
$promptLines += ""
$promptLines += "This project uses a per-room ReentrantLock model: GameSessionService.roomLocks protects GameState and Room mutations."
$promptLines += "All state changes MUST happen inside executeWithLock. The lock is released before broadcasts, bot advances, or timeout scheduling — creating TOCTOU race windows that have caused multiple P0 bugs."
$promptLines += ""
$promptLines += "Known historical bugs:"
$promptLines += "- C2: Per-disconnect newSingleThreadScheduledExecutor(), never shutdown — thread leak"
$promptLines += "- C3/C4/C5: processAction/handleTimeout mutated inside lock, broadcast outside — TOCTOU"
$promptLines += "- C6: Disconnect Phase 1 (locked) captured state, Phase 2 (unlocked) used stale state"
$promptLines += "- C8: startGame findRoom (no lock) -> lock.lock() TOCTOU window"
$promptLines += ""
$promptLines += "Review this diff and check:"
$promptLines += "1. Is every read/write of GameState/Room inside executeWithLock?"
$promptLines += "2. Are broadcasts/autoPlayBots/scheduleNextTimeout called AFTER lock release?"
$promptLines += "3. Any TOCTOU gap between locked read and unlocked action?"
$promptLines += "4. Disconnect handler: Phase 1 (lock) and Phase 2 (broadcast/timeout) correct?"
$promptLines += "5. Nested lock acquisitions that could deadlock?"
$promptLines += "6. ScheduledExecutorService: shared pool? @PreDestroy? Callbacks cancelled?"
$promptLines += ""
$promptLines += "Output Format:"
$promptLines += "- P0 Critical (race/deadlock/TOCTOU): [must fix]"
$promptLines += "- P1 Warning (lock scope, ordering): [should address]"
$promptLines += "- P2 Suggestions: [optional]"
$promptLines += "- Overall: [one sentence]"
$promptLines += ""
$promptLines += "Diff to review:"
$promptLines += $diff

$fullPrompt = $promptLines -join [Environment]::NewLine

Write-Host "[MiMo] Sending concurrency review..."
$body = @{
    model    = $model
    messages = @(@{ role = "user"; content = $fullPrompt })
    temperature = 0.3
    max_tokens  = 2500
} | ConvertTo-Json -Depth 5

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/chat/completions" -Method Post -Headers @{ "Authorization" = "Bearer $apiKey"; "Content-Type" = "application/json" } -Body $body -TimeoutSec 120
    $review = $response.choices[0].message.content
    $ts = Get-Date -Format "yyyyMMdd-HHmmss"
    $outFile = "D:\poker-web\.cursor\reviews\mimo-concurrency-$ts.md"
    New-Item -ItemType Directory -Force -Path (Split-Path $outFile) | Out-Null
    $review | Out-File -FilePath $outFile -Encoding utf8
    Write-Host "[MiMo] Concurrency review saved to $outFile"
    Write-Host "--- BEGIN REVIEW ---"
    Write-Host $review
    Write-Host "--- END REVIEW ---"
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)"
}
