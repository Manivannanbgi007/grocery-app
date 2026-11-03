<#
.SYNOPSIS
    Restores one database from a backup of backup-databases.ps1 - by default into a NEW database, to check the backup.

.DESCRIPTION
    Without -Into, the dump is restored into "<database>_restored" and the number of rows of every table is compared
    with the live database: a regular restore test ("a backup that was never restored is not a backup").
    The live database is only replaced with -Into <the same name> -Force; stop the service first, and run Flyway's
    migrations afterwards by starting the service (the dump contains the flyway_schema_history table).

.EXAMPLE
    .\scripts\restore-database.ps1 -Database order_db                          # newest backup -> order_db_restored
    .\scripts\restore-database.ps1 -Database order_db -Backup backups\2026-10-28_010000
    .\scripts\restore-database.ps1 -Database order_db -Into order_db -Force    # replace the live database
#>
param(
    [Parameter(Mandatory = $true)] [string] $Database,
    [string] $Backup,
    [string] $Into = "$($Database)_restored",
    [switch] $Force
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
$rootPassword = ((Get-Content $envFile) -match '^MYSQL_ROOT_PASSWORD=') -replace '^MYSQL_ROOT_PASSWORD=', ''

if (-not $Backup) {
    $Backup = (Get-ChildItem -Directory (Join-Path $PSScriptRoot "..\backups") | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$dump = Join-Path $Backup "$Database.sql.gz"
if (-not (Test-Path $dump)) {
    Write-Host "No $Database.sql.gz in $Backup" -ForegroundColor Red
    exit 1
}
if ($Into -eq $Database -and -not $Force) {
    Write-Host "This would replace the live database $Database. Add -Force if that is what you want (stop the service first)." -ForegroundColor Red
    exit 1
}

function Invoke-Sql([string] $sql, [string] $db = "") {
    docker exec -e MYSQL_PWD=$rootPassword grocery-mysql mysql -uroot -N -B -e $sql $db
}

Write-Host "Restoring $dump into $Into" -ForegroundColor Cyan
docker cp $dump "grocery-mysql:/tmp/restore-$Database.sql.gz" | Out-Null
if ((Invoke-Sql "SHOW DATABASES") -contains $Into) {
    docker exec -e MYSQL_PWD=$rootPassword grocery-mysql mysqladmin -uroot --force drop $Into | Out-Null   # an older copy
}
docker exec -e MYSQL_PWD=$rootPassword grocery-mysql mysqladmin -uroot create $Into
docker exec -e MYSQL_PWD=$rootPassword grocery-mysql sh -c "gunzip < /tmp/restore-$Database.sql.gz | mysql -uroot $Into"
$restoreExitCode = $LASTEXITCODE
docker exec grocery-mysql rm -f "/tmp/restore-$Database.sql.gz"
if ($restoreExitCode -ne 0) {
    Write-Host "The restore FAILED" -ForegroundColor Red
    exit 1
}

# Compare: rows per table, restored vs. live (the live database may have changed since the backup)
$tables = Invoke-Sql "SHOW TABLES" $Into
Write-Host ("{0,-28} {1,10} {2,10}" -f "table", "restored", "live now")
foreach ($table in $tables) {
    $restored = Invoke-Sql "SELECT COUNT(*) FROM $table" $Into
    $live = if ($Into -ne $Database) { Invoke-Sql "SELECT COUNT(*) FROM $table" $Database } else { "-" }
    Write-Host ("{0,-28} {1,10} {2,10}" -f $table, $restored, $live)
}
Write-Host "Restored $($tables.Count) tables into $Into." -ForegroundColor Green
if ($Into -ne $Database) {
    Write-Host "Remove the copy when done: docker exec -e MYSQL_PWD=... grocery-mysql mysqladmin -uroot --force drop $Into"
}
