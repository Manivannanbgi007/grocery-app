#!/bin/bash
# -----------------------------------------------------------------------------
# The "db-shell" task (ecs-bootstrap.tf): a container with the mysql client that
# waits one hour, so scripts/db-shell.ps1 can open a MySQL prompt in it with
# `aws ecs execute-command ... --command db-connect`.
#
# db-connect reads the connection settings from the container's own environment
# (ECS put the admin password there from Secrets Manager); nothing is written to disk.
# -----------------------------------------------------------------------------
set -euo pipefail

cat > /usr/local/bin/db-connect <<'SCRIPT'
#!/bin/bash
# A session started by `aws ecs execute-command` may not have the task's
# environment: read it from the container's first process instead
while IFS='=' read -r -d '' name value; do
  case "$name" in
    DB_HOST | MASTER_USERNAME | MASTER_PASSWORD) export "$name=$value" ;;
  esac
done < /proc/1/environ

export MYSQL_PWD="$MASTER_PASSWORD"
echo "Connecting to $DB_HOST as $MASTER_USERNAME (TLS). Type 'exit' to leave."
exec mysql --host="$DB_HOST" --user="$MASTER_USERNAME" --ssl-mode=REQUIRED "$@"
SCRIPT
chmod 755 /usr/local/bin/db-connect

echo "Ready: run 'db-connect' in this container. It stops by itself after one hour."
sleep 3600
