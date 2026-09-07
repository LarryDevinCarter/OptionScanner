#!/usr/bin/env bash
# Install OptionScanner on the droplet alongside nexstep.
# Does NOT stop, modify, or restart nexstep.service.
set -euo pipefail

APP_DIR=/var/www/optionscanner
UNIT_SRC="$(cd "$(dirname "$0")" && pwd)/optionscanner.service"
UNIT_DST=/etc/systemd/system/optionscanner.service
DB_NAME=optionscanner
JAR_NAME=option-scanner-0.0.1-SNAPSHOT.jar

echo "=== OptionScanner install (coexist with nexstep) ==="
echo "This script never touches nexstep.service or /var/www/nexstep."

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Re-run with sudo." >&2
  exit 1
fi

echo "Creating ${APP_DIR}..."
mkdir -p "${APP_DIR}"
# Prefer deploy user if present (matches nexstep chown pattern); else root
if id deploy &>/dev/null; then
  chown deploy:deploy "${APP_DIR}"
fi

echo "Ensuring Postgres database '${DB_NAME}' exists (peer/local)..."
if command -v sudo >/dev/null && id postgres &>/dev/null; then
  if sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
    echo "Database ${DB_NAME} already exists."
  else
    sudo -u postgres createdb "${DB_NAME}"
    echo "Created database ${DB_NAME}."
  fi
else
  echo "WARN: could not reach local postgres as peer user; create DB '${DB_NAME}' manually." >&2
fi

echo "Installing systemd unit..."
cp "${UNIT_SRC}" "${UNIT_DST}"
systemctl daemon-reload
systemctl enable optionscanner.service

echo
echo "=== Secrets (keep out of git) ==="
echo "Place runtime config at either:"
echo "  ${APP_DIR}/application.properties"
echo "or set SPRING_CONFIG_ADDITIONAL_LOCATION to a file/dir outside the repo."
echo "Do not commit API keys, DB passwords, or SMTP credentials."
echo
if [[ ! -f "${APP_DIR}/application.properties" ]]; then
  echo "NOTE: ${APP_DIR}/application.properties is missing."
  echo "Copy from application.properties.example and fill secrets before start."
fi

if [[ ! -f "${APP_DIR}/${JAR_NAME}" ]]; then
  echo "NOTE: ${APP_DIR}/${JAR_NAME} not present yet."
  echo "CI deploy (or manual scp) must place the jar before first start."
fi

echo
echo "Start when jar + secrets are ready:"
echo "  sudo systemctl start optionscanner.service"
echo "  sudo systemctl status optionscanner.service --no-pager"
echo "Done. nexstep was not modified."
