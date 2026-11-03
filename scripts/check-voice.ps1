<#
.SYNOPSIS
    Checks the assistant's voice: the speech server (speaches, docker-compose profile "ai") reads a question aloud,
    turns it back into text, and the script prints how long both took.

.DESCRIPTION
    1. speaches answers and has both models (SPEECH_TRANSCRIPTION_MODEL, SPEECH_VOICE_MODEL in infrastructure/.env).
    2. A question is read aloud (text -> speech) and understood again (speech -> text), straight at speaches.
    3. When the gateway runs: the same through the assistant's endpoints (POST /api/v1/chat/speech, /transcriptions).
    -SaveFixture also writes the spoken question to fresh-grocery/e2e/fixtures/which-offers.wav, the "microphone"
    of the Playwright voice test (e2e/voice.spec.ts).

.EXAMPLE
    .\scripts\check-voice.ps1
    .\scripts\check-voice.ps1 -SaveFixture
#>
param(
    [string] $Speaches = "http://localhost:8000",
    [string] $Gateway = "http://localhost:8080",
    [string] $Question = "Which offers are running this week?",
    [switch] $SaveFixture
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
function Get-EnvValue([string] $Name) { return ((Get-Content $envFile) -match "^$Name=") -replace "^$Name=", "" }
$transcriptionModel = Get-EnvValue "SPEECH_TRANSCRIPTION_MODEL"
$voiceModel = Get-EnvValue "SPEECH_VOICE_MODEL"

function Write-Result([bool] $Ok, [string] $Text) {
    if ($Ok) { Write-Host "  [OK]   $Text" -ForegroundColor Green } else { Write-Host "  [FAIL] $Text" -ForegroundColor Red }
}

# Compares two sentences without capitals and punctuation ("Which offers ...?" = "which offers ...")
function Get-Words([string] $Text) { return ($Text.ToLowerInvariant() -replace "[^a-z0-9 ]", "").Trim() }

Write-Host "Speech server (speaches) at $($Speaches):" -ForegroundColor Cyan
try { Invoke-RestMethod "$Speaches/health" -TimeoutSec 5 | Out-Null }
catch { Write-Result $false "speaches does not answer - start it with .\scripts\start-infra.ps1 -Profiles ai"; exit 1 }
$installed = @((Invoke-RestMethod "$Speaches/v1/models").data | ForEach-Object { $_.id })
foreach ($model in $transcriptionModel, $voiceModel) {
    Write-Result ($installed -contains $model) "model $model"
}

$wav = Join-Path ([System.IO.Path]::GetTempPath()) "grocery-voice-check.wav"
$speechBody = @{ model = $voiceModel; voice = "af_heart"; input = $Question; response_format = "wav" } | ConvertTo-Json
$watch = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-WebRequest -Method Post -Uri "$Speaches/v1/audio/speech" -ContentType "application/json" -Body $speechBody `
    -OutFile $wav -UseBasicParsing
$speakSeconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
Write-Result ((Get-Item $wav).Length -gt 1000) "read aloud in $speakSeconds s: ""$Question"""

$watch.Restart()
$heard = (curl.exe -s -X POST "$Speaches/v1/audio/transcriptions" -F "file=@$wav" -F "model=$transcriptionModel" `
    -F "language=en" -F "vad_filter=true" | ConvertFrom-Json).text
$hearSeconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
Write-Result ((Get-Words $heard) -eq (Get-Words $Question)) "understood in $hearSeconds s: ""$heard"""

Write-Host "Through the gateway at $($Gateway):" -ForegroundColor Cyan
try {
    $watch.Restart()
    $answer = curl.exe -s -X POST "$Gateway/api/v1/chat/transcriptions" -F "audio=@$wav;type=audio/wav" | ConvertFrom-Json
    $seconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
    $shown = if ($answer.success -eq $true) { $answer.data.text } else { $answer.message }
    Write-Result ($answer.success -eq $true) "POST /api/v1/chat/transcriptions in $seconds s: ""$shown"""

    $mp3 = Join-Path ([System.IO.Path]::GetTempPath()) "grocery-voice-check.mp3"
    $watch.Restart()
    Invoke-WebRequest -Method Post -Uri "$Gateway/api/v1/chat/speech" -ContentType "application/json" `
        -Headers @{ Accept = "audio/mpeg, application/json" } -Body (@{ text = $Question } | ConvertTo-Json) -OutFile $mp3 -UseBasicParsing
    $seconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
    Write-Result ((Get-Item $mp3).Length -gt 1000) "POST /api/v1/chat/speech in $seconds s ($([math]::Round((Get-Item $mp3).Length / 1KB)) KB of mp3)"
} catch {
    Write-Host "  (skipped: the gateway or mcp-client-service does not answer - $($_.Exception.Message))" -ForegroundColor Yellow
}

if ($SaveFixture) {
    $fixture = Join-Path $PSScriptRoot "..\fresh-grocery\e2e\fixtures\which-offers.wav"
    New-Item -ItemType Directory -Force (Split-Path $fixture) | Out-Null
    Copy-Item $wav $fixture -Force
    Write-Host "Saved the spoken question for the Playwright voice test: $fixture" -ForegroundColor Cyan
}
