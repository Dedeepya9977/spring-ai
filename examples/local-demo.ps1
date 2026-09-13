$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080/api"
$developer = @{Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("developer:local-only"))}
$reviewer = @{Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("reviewer:local-only"))}
$session = Invoke-RestMethod -Method Post -Uri "$base/sessions" -Headers $developer
$headers = $developer.Clone()
$headers["Idempotency-Key"] = "learn-" + [guid]::NewGuid().ToString()
$body = @{message="Please request a credit for ORD-1001"; mode="AGENT"} | ConvertTo-Json
$run = Invoke-RestMethod -Method Post -Uri "$base/sessions/$($session.id)/runs" -Headers $headers -ContentType "application/json" -Body $body
Write-Host "Run result (inspect the exact proposed arguments):"
$run | ConvertTo-Json -Depth 10
if ($run.status -eq "WAITING_APPROVAL") {
    $choice = Read-Host "Record the displayed synthetic credit as reviewer? Type approve or deny"
    $decision = @{approve=($choice -eq "approve"); reason="Manual review during the local learning exercise"} | ConvertTo-Json
    $result = Invoke-RestMethod -Method Post -Uri "$base/runs/$($run.id)/decision" -Headers $reviewer -ContentType "application/json" -Body $decision
    $result | ConvertTo-Json -Depth 10
}
Write-Host "Reuse the same key and body to observe idempotent replay:"
Invoke-RestMethod -Method Post -Uri "$base/sessions/$($session.id)/runs" -Headers $headers -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 10
