<#
.SYNOPSIS
    Builds the Docker images and pushes them to your Amazon ECR repositories.

.DESCRIPTION
    Reads everything it needs from Terraform (terraform output): the region, the repositories and the image tag
    (image_tag in terraform.tfvars). Logs Docker in to ECR, then builds and pushes every image, or only the ones
    you name. Run `terraform apply` once before (it creates the repositories).

    The first build of all images takes a while: every service compiles with Maven inside Docker.
    A tag can be pushed only once (the repositories are immutable): for a new release, raise image_tag first.

.EXAMPLE
    .\deployment\aws\scripts\push-images.ps1                                  # every image
    .\deployment\aws\scripts\push-images.ps1 -Images auth-service,frontend   # only these two
#>
param(
    [string[]] $Images = @()
)

$ErrorActionPreference = "Stop"
$terraformDir = Join-Path $PSScriptRoot "..\terraform"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")

# One output of the Terraform configuration, as a PowerShell value
function Get-TerraformOutput([string] $name) {
    $json = terraform "-chdir=$terraformDir" output -json $name
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read the Terraform output '$name'. Run 'terraform apply' in $terraformDir first."
    }
    return ($json | Out-String | ConvertFrom-Json)
}

$region = Get-TerraformOutput "aws_region"
$registry = Get-TerraformOutput "ecr_registry"
$tag = Get-TerraformOutput "image_tag"
$platform = Get-TerraformOutput "image_platform"
$repositories = Get-TerraformOutput "ecr_repository_urls"   # image name => repository URL
$contexts = Get-TerraformOutput "image_build_contexts"      # image name => folder with its Dockerfile

$known = @($repositories.PSObject.Properties.Name)
if ($Images.Count -eq 0) { $Images = $known }
$unknown = @($Images | Where-Object { $known -notcontains $_ })
if ($unknown.Count -gt 0) {
    throw "Unknown image(s): $($unknown -join ', '). Known images: $($known -join ', ')"
}

Write-Host "Logging Docker in to $registry ..." -ForegroundColor Cyan
aws ecr get-login-password --region $region | docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) {
    throw "docker login to ECR failed. Are your AWS credentials set? Check with: aws sts get-caller-identity"
}

foreach ($image in $Images) {
    $target = "$($repositories.$image):$tag"
    $context = Join-Path $repoRoot $contexts.$image
    Write-Host ""
    Write-Host "=== $image -> $target" -ForegroundColor Cyan

    docker build --platform $platform -t $target $context
    if ($LASTEXITCODE -ne 0) { throw "Building $image failed (see the output above)." }

    docker push $target
    if ($LASTEXITCODE -ne 0) {
        throw "Pushing $image failed. If the tag already exists in ECR, raise image_tag in terraform.tfvars (tags cannot be overwritten)."
    }
}

Write-Host ""
Write-Host "Done: $($Images.Count) image(s) pushed with tag $tag." -ForegroundColor Green
