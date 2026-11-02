#!/bin/bash
# Database per service: cada serviço tem seu banco e seu usuário, dono apenas do próprio banco.
# Roda uma única vez, na criação do volume do PostgreSQL.
set -euo pipefail

for service in auth bank registry contract document routing integration notification audit operations; do
    db="hub_${service}"
    password_var="HUB_$(echo "$service" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
    password="${!password_var:-$db}"

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v db="$db" -v password="$password" <<-'EOSQL'
		CREATE ROLE :"db" LOGIN PASSWORD :'password';
		CREATE DATABASE :"db" OWNER :"db";
		REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
	EOSQL
    echo "Banco ${db} criado"
done
