#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Builds the Docker images and pushes them to your Amazon ECR repositories
# (macOS / Linux; on Windows use push-images.ps1). Needs: terraform, aws, docker, jq.
#
# Reads everything from Terraform (terraform output): region, repositories, image
# tag (image_tag in terraform.tfvars). Run `terraform apply` once before.
#
#   ./deployment/aws/scripts/push-images.sh                          # every image
#   ./deployment/aws/scripts/push-images.sh auth-service frontend    # only these
# -----------------------------------------------------------------------------
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
terraform_dir="$script_dir/../terraform"
repo_root="$(cd "$script_dir/../../.." && pwd)"

output() { terraform -chdir="$terraform_dir" output -json "$1"; }

region="$(output aws_region | jq -r .)"
registry="$(output ecr_registry | jq -r .)"
tag="$(output image_tag | jq -r .)"
platform="$(output image_platform | jq -r .)"
repositories="$(output ecr_repository_urls)"
contexts="$(output image_build_contexts)"

if [ "$#" -gt 0 ]; then
  images=("$@")
else
  images=()
  while IFS= read -r name; do images+=("$name"); done < <(echo "$repositories" | jq -r 'keys[]') # (bash 3 on macOS has no mapfile)
fi

echo "Logging Docker in to $registry ..."
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"

for image in "${images[@]}"; do
  repository="$(echo "$repositories" | jq -r --arg name "$image" '.[$name] // empty')"
  if [ -z "$repository" ]; then
    echo "Unknown image: $image. Known: $(echo "$repositories" | jq -r 'keys | join(", ")')" >&2
    exit 1
  fi
  context="$repo_root/$(echo "$contexts" | jq -r --arg name "$image" '.[$name]')"
  target="$repository:$tag"

  echo
  echo "=== $image -> $target"
  docker build --platform "$platform" -t "$target" "$context"
  docker push "$target"
done

echo
echo "Done: ${#images[@]} image(s) pushed with tag $tag."
