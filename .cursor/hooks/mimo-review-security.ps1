$apiKey = "sk-csnvdf0u6rgk0gj4az4q9mn3n2oa67b8890xxxi5p4oywe60"
$baseUrl = "https://api.xiaomimimo.com/v1"
$model = "mimo-v2.5-pro"

Push-Location D:\poker-web
$diff = (& git diff origin/master...HEAD -- . ':!*.lock' ':!package-lock.json' ':!*.png' ':!*.jpg' ':!*.svg' 2>&1 | Out-String)
if ($diff.Length -gt 40000) { $diff = $diff.Substring(0, 40000) + [Environment]::NewLine + "[... truncated ...]" }
Pop-Location

$promptLines = @()
$promptLines += "You are a security reviewer for a multiplayer game server (Spring Boot)."
$promptLines += ""
$promptLines += "Known historical bugs:"
$promptLines += "- C13: JoinRoomRequest.password existed but joinRoom never checked it — dead code"
$promptLines += "- C12: GameActionRequest no @NotBlank — null playerId caused NPE -> 500"
$promptLines += "- M8: borrowChips didn't verify playerId in room"
$promptLines += "- Password leak: Room.getPassword() serialized to API responses without @JsonIgnore"
$promptLines += ""
$promptLines += "Review this diff and check:"
$promptLines += "1. DTO fields: @NotBlank/@NotNull validated? @Valid/@Validated on controller?"
$promptLines += "2. Password/auth: checked on join? excluded from responses (@JsonIgnore)?"
$promptLines += "3. Authorization: every endpoint verifies caller identity and permissions?"
$promptLines += "4. Are secrets/passwords ever logged or serialized to client responses?"
$promptLines += "5. Input injection: are playerId/roomId/action strings validated?"
$promptLines += ""
$promptLines += "Output Format:"
$promptLines += "- P0 Critical (auth bypass / data leak): [must fix]"
$promptLines += "- P1 Warning (missing validation): [should address]"
$promptLines += "- P2 Suggestions: [optional]"
$promptLines += "- Overall: [one sentence]"
$promptLines += ""
$promptLines += "Diff to review:"
$promptLines += $diff

$fullPrompt = $promptLines -join [Environment]::NewLine

Write-Host "[MiMo] Sending security review..."
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
    $outFile = "D:\poker-web\.cursor\reviews\mimo-security-$ts.md"
    New-Item -ItemType Directory -Force -Path (Split-Path $outFile) | Out-Null
    $review | Out-File -FilePath $outFile -Encoding utf8
    Write-Host "[MiMo] Security review saved to $outFile"
    Write-Host "--- BEGIN REVIEW ---"
    Write-Host $review
    Write-Host "--- END REVIEW ---"
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)"
}
