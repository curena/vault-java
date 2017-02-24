#!/bin/sh

vault mount postgresql;
vault write postgresql/config/connection connection_url="postgresql://root:vaulttest@vaulttest.ciuvljjni7uo.us-west-1.rds.amazonaws.com:5432/postgres";
vault write postgresql/config/lease lease=1h lease_max=24h;
vault write postgresql/roles/readonly     sql="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";"

