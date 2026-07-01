#!/usr/bin/env bash
# One-time Let's Encrypt certificate bootstrap for the nginx + certbot compose setup.
# Adapted from the well-known nginx-certbot docker pattern.
#
# Prerequisites:
#   - DNS A record api.stdiodh.xyz -> this server's public IP, already propagated
#   - EC2 security group allows inbound 80 and 443
#   - `docker compose build` has been run
#
# Usage: set EMAIL below, then run:  chmod +x init-letsencrypt.sh && ./init-letsencrypt.sh
set -e

DOMAIN="api.stdiodh.xyz"
EMAIL="CHANGE_ME@example.com"      # <-- set a real email (Let's Encrypt expiry notices)
STAGING=1                          # 1 = Let's Encrypt staging (for testing, avoids rate limits); set 0 for real certs
RSA_KEY_SIZE=4096
DATA_PATH="./certbot"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found" >&2; exit 1
fi

echo "### Creating directories ..."
mkdir -p "$DATA_PATH/www" "$DATA_PATH/conf/live/$DOMAIN"

echo "### Creating a dummy certificate so nginx can start ..."
docker compose run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=localhost'" certbot

echo "### Starting nginx ..."
docker compose up --force-recreate -d nginx

echo "### Deleting dummy certificate ..."
docker compose run --rm --entrypoint "\
  rm -Rf /etc/letsencrypt/live/$DOMAIN && \
  rm -Rf /etc/letsencrypt/archive/$DOMAIN && \
  rm -Rf /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

echo "### Requesting the real certificate ..."
STAGING_ARG=""
if [ "$STAGING" != "0" ]; then STAGING_ARG="--staging"; fi

docker compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    --email $EMAIL \
    -d $DOMAIN \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --no-eff-email \
    --force-renewal" certbot

echo "### Reloading nginx ..."
docker compose exec nginx nginx -s reload

echo "### Done. If STAGING=1, verify it works, then set STAGING=0 and re-run to get a trusted cert."
