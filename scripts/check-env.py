#!/usr/bin/env python3
"""Validate EduTrack deployment settings without printing secret values."""
import os
import re
import sys
from pathlib import Path


def load_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{number}: expected KEY=VALUE")
        key, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise ValueError(f"{path}:{number}: invalid variable name")
        values[key] = value.strip().strip('"').strip("'")
    return values


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".env")
    if not path.is_file():
        print(f"Missing environment file: {path}", file=sys.stderr)
        return 2
    try:
        values = load_file(path)
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 2
    values.update(os.environ)
    production = values.get("APP_ENV") == "production" or values.get("SPRING_PROFILES_ACTIVE") == "prod"
    backend_service = bool(values.get("DATABASE_URL"))
    database_names = ["DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD"] if backend_service else ["DB_NAME", "DB_USERNAME", "DB_PASSWORD"]
    admin_password_name = "ADMIN_PASSWORD" if backend_service else "ADMIN_INITIAL_PASSWORD"
    cors_name = "CORS_ORIGIN" if backend_service else "CORS_ALLOWED_ORIGINS"
    required = database_names + ["JWT_SECRET", "ADMIN_EMAIL", admin_password_name, "FRONTEND_URL", cors_name]
    if production:
        required += (["SMTP_HOST", "SMTP_USER", "SMTP_PASSWORD", "MAIL_FROM"] if backend_service
                     else ["MAIL_HOST", "MAIL_USERNAME", "MAIL_PASSWORD", "MAIL_FROM"])
    errors = [f"Missing {name}" for name in required if not values.get(name)]
    if production:
        if values.get("APP_ENV") != "production" or values.get("SPRING_PROFILES_ACTIVE") != "prod":
            errors.append("Production requires APP_ENV=production and SPRING_PROFILES_ACTIVE=prod")
        for name in required:
            value = values.get(name, "").lower()
            if any(marker in value for marker in ("change_me", "replace-with", "password123")):
                errors.append(f"Replace placeholder {name}")
        if len(values.get("JWT_SECRET", "").encode()) < 32:
            errors.append("JWT_SECRET must be at least 32 bytes")
        if len(values.get(admin_password_name, "")) < 16:
            errors.append(f"{admin_password_name} must be at least 16 characters")
        if values.get("SMTP_ENABLED") != "true":
            errors.append("SMTP_ENABLED must be true")
        if values.get("SMTP_AUTH") != "true" or values.get("SMTP_STARTTLS") != "true":
            errors.append("SMTP_AUTH and SMTP_STARTTLS must be true in production")
        if not values.get("FRONTEND_URL", "").startswith("https://"):
            errors.append("FRONTEND_URL must use HTTPS")
        if any(not origin.strip().startswith("https://") for origin in values.get(cors_name, "").split(",")):
            errors.append(f"{cors_name} must contain only HTTPS origins")
        smtp_host_name = "SMTP_HOST" if backend_service else "MAIL_HOST"
        if values.get(smtp_host_name) in ("mailpit", "localhost", "127.0.0.1"):
            errors.append(f"Production {smtp_host_name} must be an external SMTP host")
        for name in ("ADMIN_EMAIL", "MAIL_FROM"):
            if values.get(name, "").endswith((".local", "@localhost")):
                errors.append(f"{name} must use a real domain in production")
        if backend_service and not values.get("DATABASE_URL", "").startswith("jdbc:postgresql://"):
            errors.append("DATABASE_URL must be a PostgreSQL JDBC URL")
    for name in ("JWT_ACCESS_TOKEN_EXPIRATION", "JWT_REFRESH_TOKEN_EXPIRATION"):
        value = values.get(name, "")
        divisor = 60 if name == "JWT_ACCESS_TOKEN_EXPIRATION" else 86400
        if not value.isdecimal() or int(value) <= 0 or int(value) % divisor:
            errors.append(f"{name} must be a positive multiple of {divisor} seconds")
    if errors:
        print("Environment check failed:\n- " + "\n- ".join(errors), file=sys.stderr)
        return 1
    print(f"Environment check passed ({'production' if production else 'development'}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
