<#
.SYNOPSIS
    Backs up every service database (MySQL) and the stored invoice PDFs into backups\<date-time>\.

.DESCRIPTION
    For every database named *_db: mysqldump --single-transaction (a consistent snapshot of that database while the
    services keep working; nothing is locked), compressed with gzip. Plus the folder of stored invoices
    (order-service\data\invoices) and the MySQL binlog position at the time of the backup (for a point-in-time restore,
    docs/resilience.md "Backups"). Keeps the newest -Keep backups and deletes older ones.

    Not backed up, on purpose: Redis (rate limits, partner presence, live locations: temporary, rebuilt by use),
    Elasticsearch (rebuilt from the product events: scripts\rebuild-product-index.ps1), Kafka (the events come from the
    outbox tables, which are in the dump).

    Restore one database: .\scripts\restore-database.ps1 -Database order_db

.EXAMPLE
    .\scripts\backup-databases.ps1
    .\scripts\backup-databases.ps1 -Destination E:\grocery-backups -Keep 14
#>
param(
    [string] $Destination = (Join-Path $PSScriptRoot "..\backups"),
    [int] $Keep = 7
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
$rootPassword = ((Get-Content $envFile) -match '^MYSQL_ROOT_PASSWORD=') -replace '^MYSQL_ROOT_PASSWORD=', ''
$folder = Join-Path $Destination (Get-Date -Format "yyyy-MM-dd_HHmmss")
New-Item -ItemType Directory -Force $folder | Out-Null
$folder = (Resolve-Path $folder).Path

# The databases of the services: every database whose name ends with _db
$databases = docker exec -e MYSQL_PWD=$rootPassword grocery-mysql mysql -uroot -N -B -e "SHOW DATABASES" |
        Where-Object { $_ -like "*_db" }
if ($LASTEXITCODE -ne 0 -or -not $databases) {
    Write-Host "Cannot read the database list. Is MySQL running (docker ps)?" -ForegroundColor Red
    exit 1
}

# Where the binlog is now: with it, changes after this backup can be replayed (point-in-time restore)
$binlog = docker exec -e MYSQL_PWD=$rootPassword grocery-mysql mysql -uroot -N -B -e "SHOW BINARY LOG STATUS"
$info = @("Backup of $(Get-Date -Format o)", "Binlog position at the start: $binlog", "")

Write-Host "Backing up $($databases.Count) databases into $folder" -ForegroundColor Cyan
foreach ($database in $databases) {
    $started = Get-Date
    # Inside the container: dump and compress to a temporary file (binary data never passes through PowerShell)
    docker exec -e MYSQL_PWD=$rootPassword grocery-mysql sh -c "mysqldump -uroot --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces $database | gzip > /tmp/backup-$database.sql.gz"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  $database FAILED" -ForegroundColor Red
        exit 1
    }
    docker cp "grocery-mysql:/tmp/backup-$database.sql.gz" (Join-Path $folder "$database.sql.gz") | Out-Null
    docker exec grocery-mysql rm -f "/tmp/backup-$database.sql.gz"
    $size = (Get-Item (Join-Path $folder "$database.sql.gz")).Length
    $line = "{0,-18} {1,10} bytes  {2:0.0} s" -f $database, $size, ((Get-Date) - $started).TotalSeconds
    Write-Host "  $line"
    $info += $line
}

# The stored invoice PDFs (order-service: app.invoice.storage-dir) belong to order_db's invoices table
$invoices = Join-Path $PSScriptRoot "..\order-service\data\invoices"
if (Test-Path $invoices) {
    Copy-Item -Recurse $invoices (Join-Path $folder "invoices")
    $count = (Get-ChildItem -Recurse -File (Join-Path $folder "invoices")).Count
    Write-Host "  invoices            $count file(s)"
    $info += "invoices: $count file(s)"
}
$info | Set-Content -Encoding utf8 (Join-Path $folder "backup-info.txt")

# Keep the newest $Keep backups
$old = Get-ChildItem -Directory $Destination | Sort-Object Name -Descending | Select-Object -Skip $Keep
foreach ($backup in $old) {
    Remove-Item -Recurse -Force $backup.FullName
    Write-Host "Deleted the old backup $($backup.Name)"
}
Write-Host "Done: $folder" -ForegroundColor Green
