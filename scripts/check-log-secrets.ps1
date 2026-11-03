<#
.SYNOPSIS
    Searches the logs in Loki for data that must never be logged: tokens, passwords, keys, card numbers,
    email addresses and phone numbers.

.DESCRIPTION
    Logs are kept for days and read by many people, so they must not contain secrets or personal data
    (docs/observability.md, "Sensitive data in logs"). For each kind of data this script asks Loki how many log lines
    of every service match, shows up to 3 of them with the match hidden, and exits with 1 when anything was found,
    so it can also run in a pipeline against a test environment.
    Needs Loki (start-infra.ps1 -Profiles monitoring) and services started with LOKI_ENABLED=true.

.EXAMPLE
    .\scripts\check-log-secrets.ps1                 # the last 24 hours
    .\scripts\check-log-secrets.ps1 -Since 30m      # only the last 30 minutes (e.g. to confirm a fix)
#>
param(
    [string] $Since = "24h",
    [string] $LokiUrl = "http://localhost:3100"
)

# Kafka prints its whole configuration at start-up ("ssl.key.password = null"); it hides real passwords itself,
# so those lines are skipped where they would be reported for the word "password" alone.
$notKafkaConfig = '| logger != "org.apache.kafka.common.config.AbstractConfig"'

# Regex = RE2 syntax (Loki). It is also used to hide the match in the examples, so keep it valid .NET syntax too.
# Numbers must not touch letters, digits or "-" on either side: otherwise the digits inside ids and correlation ids
# (UUIDs such as 07c45043-...-3987654321ab) would count as card or phone numbers.
$checks = @(
    @{ Name = "Access token (JWT)";    Regex = 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}' },
    @{ Name = "Authorization header";  Regex = '(?i)bearer [A-Za-z0-9._~+/-]{20,}' },
    @{ Name = "Password";              Regex = '(?i)(password|passwd|pwd)["'']?\s*[:=]'; Extra = $notKafkaConfig },
    @{ Name = "Seed password";         Regex = 'Password@123' },
    @{ Name = "Secret or API key";     Regex = '(?i)(secret|api[_-]?key|access[_-]?key)["'']?\s*[:=]\s*["'']?[A-Za-z0-9/+_-]{8,}'; Extra = $notKafkaConfig },
    @{ Name = "Razorpay key";          Regex = 'rzp_(test|live)_[A-Za-z0-9]{6,}' },
    @{ Name = "Card number";           Regex = '(^|[^0-9A-Za-z-])([0-9]{4}[ -]?){3}[0-9]{4}([^0-9A-Za-z-]|$)' },
    @{ Name = "Email address";         Regex = '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z0-9.-]*[A-Za-z]{2,}' },
    @{ Name = "Phone number (India)";  Regex = '(^|[^0-9A-Za-z-])[6-9][0-9]{9}([^0-9A-Za-z-]|$)' }
)

function Invoke-Loki([string] $path, [hashtable] $parameters) {
    $query = ($parameters.GetEnumerator() | ForEach-Object { "$($_.Key)=$([uri]::EscapeDataString([string]$_.Value))" }) -join "&"
    Invoke-RestMethod -Uri "$LokiUrl$path`?$query" -TimeoutSec 60
}

Write-Host "Sensitive data in the logs of the last $Since (Loki $LokiUrl)" -ForegroundColor Cyan
$found = 0
foreach ($check in $checks) {
    # `...` is a LogQL raw string: the regex needs no extra escaping
    $selector = '{app=~".+"} ' + $check.Extra + ' |~ `' + $check.Regex + '`'
    $counts = Invoke-Loki "/loki/api/v1/query" @{ query = "sum by (app) (count_over_time($selector [$Since]))" }
    $perService = @($counts.data.result | ForEach-Object { "$($_.metric.app): $($_.value[1])" })

    if ($perService.Count -eq 0) {
        Write-Host ("  [OK]    {0}" -f $check.Name) -ForegroundColor Green
        continue
    }
    $found++
    Write-Host ("  [FOUND] {0}  ({1})" -f $check.Name, ($perService -join ", ")) -ForegroundColor Red
    $examples = Invoke-Loki "/loki/api/v1/query_range" @{ query = $selector; since = $Since; limit = 3 }
    foreach ($stream in $examples.data.result) {
        foreach ($entry in $stream.values) {
            $line = [regex]::Replace($entry[1], $check.Regex, "[HIDDEN]") -replace '\s+', ' '
            Write-Host ("          {0}: {1}" -f $stream.stream.app, $line.Substring(0, [Math]::Min(180, $line.Length)))
        }
    }
}

if ($found -gt 0) {
    Write-Host "$found kind(s) of sensitive data found. Fix the log statement (log an id instead), then check again with -Since." -ForegroundColor Red
    exit 1
}
Write-Host "Nothing found." -ForegroundColor Green
exit 0
