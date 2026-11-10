#!/bin/bash
# -----------------------------------------------------------------------------
# One-time database setup, run as the ECS task "db-init" (ecs-bootstrap.tf).
# Safe to run again: it only creates what is missing and resets the passwords to
# the ones in Secrets Manager.
#
# The same as infrastructure/mysql/init/01-create-databases-and-users.sql on a
# laptop, but with generated passwords:
#   - every service's database <db>_db and its user <db>_user (no rights elsewhere)
#   - the user Debezium reads the binary log with
#
# Environment (set by the task definition; the passwords come from Secrets Manager):
#   DB_HOST, MASTER_USERNAME, MASTER_PASSWORD, DATABASES ("auth customer ..."),
#   <DB>_DB_PASSWORD for each database, DEBEZIUM_DB_PASSWORD
# -----------------------------------------------------------------------------
set -euo pipefail

# The mysql client reads the password from MYSQL_PWD, so it never shows in a process list
export MYSQL_PWD="$MASTER_PASSWORD"

run_sql() {
  mysql --host="$DB_HOST" --user="$MASTER_USERNAME" --ssl-mode=REQUIRED --batch
}

echo "Waiting for MySQL at $DB_HOST ..."
for attempt in $(seq 1 30); do
  if echo "SELECT 1;" | run_sql > /dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    echo "MySQL did not answer within 5 minutes." >&2
    exit 1
  fi
  sleep 10
done

for db in $DATABASES; do
  password_variable="$(echo "${db}_DB_PASSWORD" | tr '[:lower:]' '[:upper:]')"
  password="${!password_variable}"
  echo "Database ${db}_db with user ${db}_user"
  run_sql <<SQL
CREATE DATABASE IF NOT EXISTS \`${db}_db\`;
CREATE USER IF NOT EXISTS '${db}_user'@'%' IDENTIFIED BY '${password}';
ALTER USER '${db}_user'@'%' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON \`${db}_db\`.* TO '${db}_user'@'%';
SQL
done

echo "Debezium user (reads the binary log for Kafka Connect)"
run_sql <<SQL
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_DB_PASSWORD}';
ALTER USER 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_DB_PASSWORD}';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, LOCK TABLES ON *.* TO 'debezium'@'%';
SQL

# RDS deletes the binary log quickly by default; Debezium must be able to read
# what happened while it was stopped (a restart, a deployment)
echo "Keeping the binary log for 24 hours"
echo "CALL mysql.rds_set_configuration('binlog retention hours', 24);" | run_sql

echo "Done: $(echo "$DATABASES" | wc -w) databases are ready."
