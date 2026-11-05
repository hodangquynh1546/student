#!/bin/sh
set -eu
: "${JWT_ACCESS_TOKEN_EXPIRATION:=900}"
: "${JWT_REFRESH_TOKEN_EXPIRATION:=604800}"

if [ "${APP_ENV:-development}" = "production" ] && [ "${SPRING_PROFILES_ACTIVE:-}" != "prod" ]; then
  echo "Production requires SPRING_PROFILES_ACTIVE=prod" >&2
  exit 1
fi

for name in JWT_ACCESS_TOKEN_EXPIRATION JWT_REFRESH_TOKEN_EXPIRATION; do
  eval "value=\${$name:-}"
  case "$value" in
    ''|*[!0-9]*) echo "$name must be a positive number of seconds" >&2; exit 1 ;;
  esac
  if [ "$value" -le 0 ]; then
    echo "$name must be positive" >&2
    exit 1
  fi
done

if [ -n "${JWT_ACCESS_TOKEN_EXPIRATION:-}" ]; then
  if [ $((JWT_ACCESS_TOKEN_EXPIRATION % 60)) -ne 0 ]; then
    echo "JWT_ACCESS_TOKEN_EXPIRATION must be whole minutes" >&2
    exit 1
  fi
  JWT_ACCESS_MINUTES=$((JWT_ACCESS_TOKEN_EXPIRATION / 60))
  export JWT_ACCESS_MINUTES
fi
if [ -n "${JWT_REFRESH_TOKEN_EXPIRATION:-}" ]; then
  if [ $((JWT_REFRESH_TOKEN_EXPIRATION % 86400)) -ne 0 ]; then
    echo "JWT_REFRESH_TOKEN_EXPIRATION must be whole days" >&2
    exit 1
  fi
  REFRESH_DAYS=$((JWT_REFRESH_TOKEN_EXPIRATION / 86400))
  export REFRESH_DAYS
fi

if [ "${SPRING_PROFILES_ACTIVE:-}" = "prod" ] || [ "${APP_ENV:-}" = "production" ]; then
  for name in DATABASE_URL DATABASE_USER DATABASE_PASSWORD JWT_SECRET ADMIN_EMAIL ADMIN_PASSWORD SMTP_HOST SMTP_USER SMTP_PASSWORD MAIL_FROM FRONTEND_URL CORS_ORIGIN; do
    eval "value=\${$name:-}"
    if [ -z "$value" ]; then
      echo "Missing required production setting: $name" >&2
      exit 1
    fi
    case "$value" in
      *CHANGE_ME*|*replace-with*|*example.com*) echo "Unsafe production setting: $name" >&2; exit 1 ;;
    esac
  done
  if [ "${#JWT_SECRET}" -lt 32 ]; then
    echo "JWT_SECRET must contain at least 32 characters" >&2
    exit 1
  fi
  if [ "${#ADMIN_PASSWORD}" -lt 16 ]; then
    echo "ADMIN_PASSWORD must contain at least 16 characters" >&2
    exit 1
  fi
  if [ "${SMTP_ENABLED:-}" != "true" ]; then
    echo "SMTP_ENABLED must be true in production" >&2
    exit 1
  fi
  case "${ADMIN_EMAIL}|${MAIL_FROM}" in
    *".local"*|*"@localhost"*) echo "Production email addresses must use real domains" >&2; exit 1 ;;
  esac
  case "${SMTP_HOST}" in
    mailpit|localhost|127.0.0.1) echo "Production SMTP_HOST must point to a configured mail server" >&2; exit 1 ;;
  esac
fi

exec java ${JAVA_OPTS:-} -jar /app/edutrack.jar
