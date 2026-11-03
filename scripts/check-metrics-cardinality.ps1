<#
.SYNOPSIS
    Checks that the metrics in Prometheus stay small: no metric with too many series, no label with too many values,
    and no request metric with a real id in its "uri" label.

.DESCRIPTION
    Every combination of label values is a separate time series in Prometheus. One label filled with ids, user names
    or raw URLs ("high cardinality") turns one metric into millions of series: Prometheus gets slow and runs out of
    memory, for every service at once. The usual mistake is a request metric with uri="/api/v1/orders/174" instead of
    the route template uri="/api/v1/orders/{orderId}" (e.g. a URL built with string concatenation in a RestClient
    call). docs/observability.md, "Cardinality".
    The script reads Prometheus' own statistics (/api/v1/status/tsdb) and exits with 1 when a limit is exceeded.

.EXAMPLE
    .\scripts\check-metrics-cardinality.ps1
    .\scripts\check-metrics-cardinality.ps1 -MaxSeriesPerMetric 10000
#>
param(
    [string] $PrometheusUrl = "http://localhost:9090",
    [int] $MaxSeries = 200000,             # all series together (here today: about 36,000)
    [int] $MaxSeriesPerMetric = 30000,     # the biggest today: http_server_requests_seconds_bucket, about 13,000
    [int] $MaxValuesPerLabel = 1000        # the most today: about 430 ("name"), "uri" about 100
)

function Get-Prometheus([string] $path) {
    (Invoke-RestMethod -Uri "$PrometheusUrl$path" -TimeoutSec 30).data
}

$problems = 0
$stats = Get-Prometheus "/api/v1/status/tsdb?limit=10"

$total = [int]$stats.headStats.numSeries
Write-Host ("Series in Prometheus: {0} (limit {1})" -f $total, $MaxSeries) -ForegroundColor Cyan
if ($total -gt $MaxSeries) { $problems++; Write-Host "  [TOO MANY] all series together" -ForegroundColor Red }

Write-Host "Metrics with the most series (limit $MaxSeriesPerMetric each):" -ForegroundColor Cyan
foreach ($metric in $stats.seriesCountByMetricName) {
    $tooMany = [int]$metric.value -gt $MaxSeriesPerMetric
    if ($tooMany) { $problems++ }
    Write-Host ("  {0} {1,7}  {2}" -f $(if ($tooMany) { "[TOO MANY]" } else { "[OK]      " }), [int]$metric.value, $metric.name) `
        -ForegroundColor $(if ($tooMany) { "Red" } else { "Gray" })
}

# "__name__" counts the metric names and "le" the histogram buckets: both are many by design
Write-Host "Labels with the most different values (limit $MaxValuesPerLabel each):" -ForegroundColor Cyan
foreach ($label in $stats.labelValueCountByLabelName | Where-Object { $_.name -notin @("__name__", "le") }) {
    $tooMany = [int]$label.value -gt $MaxValuesPerLabel
    if ($tooMany) { $problems++ }
    Write-Host ("  {0} {1,7}  {2}" -f $(if ($tooMany) { "[TOO MANY]" } else { "[OK]      " }), [int]$label.value, $label.name) `
        -ForegroundColor $(if ($tooMany) { "Red" } else { "Gray" })
}

# The request metrics must name the route template, never a real id: a number or a UUID as a path segment
$query = 'count by (service, uri) ({__name__=~"http_server_requests_seconds_count|http_client_requests_seconds_count", uri=~".*/[0-9]+(/.*)?|.*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*"})'
$withIds = @((Get-Prometheus "/api/v1/query?query=$([uri]::EscapeDataString($query))").result)
Write-Host "Request metrics with a real id in the uri label:" -ForegroundColor Cyan
if ($withIds.Count -eq 0) {
    Write-Host "  [OK]       none (every uri is a template such as /api/v1/orders/{orderId})" -ForegroundColor Gray
}
foreach ($series in $withIds) {
    $problems++
    Write-Host ("  [ID]       {0}: uri={1}  -> use a URI template (uri(""/x/{{id}}"", id)), not string concatenation" -f $series.metric.service, $series.metric.uri) -ForegroundColor Red
}

if ($problems -gt 0) {
    Write-Host "$problems problem(s). Find the code that adds the label (docs/observability.md, Cardinality)." -ForegroundColor Red
    exit 1
}
Write-Host "The metrics are within the limits." -ForegroundColor Green
exit 0
