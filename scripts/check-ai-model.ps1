<#
.SYNOPSIS
    Checks that Ollama answers and that the chat model can call tools (what the AI assistant needs).

.DESCRIPTION
    Sends one question with one tool ("searchProducts") to Ollama and checks that the model asks to call that tool,
    instead of answering from its own memory. Prints how long the model took.
    Start Ollama first: .\scripts\start-infra.ps1 -Profiles ai

.EXAMPLE
    .\scripts\check-ai-model.ps1
    .\scripts\check-ai-model.ps1 -Model qwen3:8b
#>
param(
    [string] $Model = "",
    [string] $OllamaUrl = "http://localhost:11434"
)

# Same default model as the services (infrastructure/.env)
if (-not $Model) {
    $envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
    $Model = ((Get-Content $envFile) -match '^OLLAMA_CHAT_MODEL=') -replace '^OLLAMA_CHAT_MODEL=', ''
}

try {
    $tags = Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 5
}
catch {
    Write-Host "Ollama is not reachable at $OllamaUrl. Start it with: .\scripts\start-infra.ps1 -Profiles ai" -ForegroundColor Red
    exit 1
}
if (-not ($tags.models | Where-Object { $_.name -eq $Model })) {
    Write-Host "Model $Model is not downloaded. Download it with: docker exec grocery-ollama ollama pull $Model" -ForegroundColor Red
    exit 1
}

$request = @{
    model    = $Model
    stream   = $false
    think    = $false                       # no reasoning text: faster, and the assistant does not need it
    options  = @{ temperature = 0.1 }
    messages = @(
        @{ role = "system"; content = "You help customers of a grocery shop. Always use the tools for product facts." },
        @{ role = "user"; content = "How much is Amul Butter?" }
    )
    tools    = @(
        @{
            type     = "function"
            function = @{
                name        = "searchProducts"
                description = "Find products of the shop by name. Use it for every question about a product, its price or stock."
                parameters  = @{
                    type       = "object"
                    properties = @{ query = @{ type = "string"; description = "Words to search for, e.g. 'amul butter'" } }
                    required   = @("query")
                }
            }
        }
    )
} | ConvertTo-Json -Depth 10

Write-Host "Asking $Model (the first answer also loads the model, so it can take a minute on a CPU)..." -ForegroundColor Cyan
$watch = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $response = Invoke-RestMethod -Uri "$OllamaUrl/api/chat" -Method Post -Body $request -ContentType "application/json" -TimeoutSec 300
}
catch {
    Write-Host "Ollama failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$seconds = [math]::Round($watch.Elapsed.TotalSeconds, 1)

$call = $response.message.tool_calls | Select-Object -First 1
if ($call -and $call.function.name -eq "searchProducts") {
    $arguments = $call.function.arguments | ConvertTo-Json -Compress
    Write-Host "  [OK]   $Model called searchProducts $arguments in $seconds s" -ForegroundColor Green
    exit 0
}
Write-Host "  [FAIL] $Model answered without calling the tool ($seconds s): $($response.message.content)" -ForegroundColor Red
exit 1
