package se.jhaals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Java API for Hashicorp's Vault project. A tool for managing secrets.
 *
 * @see <a href="https://vaultproject.io/">https://vaultproject.io/</a>
 */
public class Vault {

    private final String vaultToken;
    private final Client client;
    private WebTarget baseTarget;

    /**
     * Initialize Vault
     *
     * @param vaultServer http address to vault server, example: http://127.0.0.1:8200
     * @param vaultToken  vault token used to authenticate requests
     */
    public Vault(String vaultServer, String vaultToken) {
        this.vaultToken = vaultToken;
        this.client = ClientBuilder.newBuilder().register(JacksonJaxbJsonProvider.class).build();
        this.baseTarget = client.target(vaultServer);
    }

    /**
     * Initialize Vault with custom Jersey HTTP client, for fine tuning and proxy configurations
     *
     * @param vaultServer http address to vault server, example: http://127.0.0.1:8200
     * @param vaultToken  vault token used to authenticate requests
     * @param client      custom Jersey client
     */
    public Vault(String vaultServer, String vaultToken, Client client) {
        this.vaultToken = vaultToken;
        this.client = client;
        this.baseTarget = client.target(vaultServer);
    }

    /**
     * Read value from vault
     *
     * @param path the vault path where secret is stored
     * @return the response object containing your secret
     * @throws VaultException if API returns anything except 200
     */
    public VaultResponse read(String path) {
        WebTarget target = baseTarget.path(String.format("/v1/%s", path));
        Response response = null;

        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .get(Response.class);
            if (response.getStatus() == 200) {
                return response.readEntity(VaultResponse.class);
            }
            ErrorResponse error = response.readEntity(ErrorResponse.class);
            for (String e : error.getErrors()) {
                System.out.println("Status: " + response.getStatus() + " Error: " + e);
            }
            throw new VaultException(response.getStatus(), error.getErrors());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Write key/value secret to vault
     *
     * @param path   where to write secret
     * @param secret A key, paired with an associated value, to be held at the given location.
     *               Multiple key/value pairs can be specified, and all will be returned on a read operation.
     *               The generic backend use 'value' as key
     * @throws VaultException if operation fails
     */
    public VaultResponse write(String path, Map<String, String> secret) {
        WebTarget target = baseTarget.path(String.format("/v1/%s", path));
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .post(Entity.entity(secret, MediaType.APPLICATION_JSON_TYPE));

            if (response.getStatus() == 204) {
                return response.readEntity(VaultResponse.class);
            } else {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                outputErrors(response.getStatus(), error);
                throw new VaultException(response.getStatus(), error.getErrors());
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Renew lease for a secret.
     *
     * @param lease_id   Secret lease id
     * @param parameters A key, paired with an associated value, to be held at the given location.
     *               Multiple key/value pairs can be specified.
     * @throws VaultException if operation fails
     */
    public VaultResponse renew(String lease_id, Map<String, String> parameters) {
        WebTarget target = baseTarget.path(String.format("/v1/sys/renew/%s", lease_id));
        Response response = null;
        VaultResponse vaultResponse = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .put(Entity.entity(parameters, MediaType.APPLICATION_JSON_TYPE));


            if (response.getStatus() >= 400) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }

            vaultResponse = response.readEntity(VaultResponse.class);

        } finally {
            if (response != null) {
                response.close();
            }
        }

        return vaultResponse;
    }

    /**
     * Revoke a secret immediately.
     *
     * @param lease_id   secret lease id
     * @throws VaultException if operation fails
     */
    public void revoke(String lease_id) {
        WebTarget target = baseTarget.path(String.format("/v1/sys/revoke/%s", lease_id));
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .put(Entity.entity(new HashMap<>(), MediaType.APPLICATION_JSON_TYPE));

            if (response.getStatus() >= 400) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Delete path from vault
     *
     * @param path to be deleted
     * @throws VaultException containing error reason
     */
    public void delete(String path) {
        WebTarget target = baseTarget.path(String.format("/v1/%s", path));
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .delete();

            if (response.getStatus() != 204) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Get status from Vault
     *
     * @return VaultStatus containing shares, progress, shards and seal status
     * @throws VaultException on non 200 status codes
     */
    public VaultStatus getStatus() {

        WebTarget target = baseTarget.path("/v1/sys/seal-status");
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .get();
            if (response.getStatus() != 200) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }
            return response.readEntity(VaultStatus.class);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * @param token to lookup
     * @return TokenResponse where "data" contains token information
     */
    public TokenResponse lookupToken(String token) {
        WebTarget target = baseTarget.path("/v1/auth/token/lookup/" + token);
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .get();
            if (response.getStatus() != 200) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }

            return response.readEntity(TokenResponse.class);

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Create new Vault token used for requests
     *
     * @param tokenCreateRequest containing properties such as ttl, policies, ttl.
     *                           It's easiest to use the TokenCreateRequestBuilder to generate request.
     * @return TokenResponse containing new token properties
     */
    public TokenResponse createToken(TokenCreateRequest tokenCreateRequest) {
        WebTarget target = baseTarget.path("/v1/auth/token/create");
        Response response = null;
        try {
            response = target.request()
                    .accept("application/json")
                    .header("X-Vault-Token", this.vaultToken)
                    .post(Entity.entity(tokenCreateRequest, MediaType.APPLICATION_JSON_TYPE));
            if (response.getStatus() != 200) {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                throw new VaultException(response.getStatus(), error.getErrors());
            }

            return response.readEntity(TokenResponse.class);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static class ErrorResponse {
        @JsonProperty
        private List<String> errors;

        public List<String> getErrors() {
            return errors;
        }
    }

    private void outputErrors(int status, ErrorResponse error) {
        for (String e : error.getErrors()) {
            System.out.println("Status: " + status + " Error: " + e);
        }
    }

}