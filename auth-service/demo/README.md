# Demo Scripts

These assets make the TrustPlatform auth demo repeatable from either Postman or the shell.

## Prerequisites

Run the service with:

```bash
BOOTSTRAP_ADMIN_EMAIL=demo-admin@example.com
```

That email is used by the Postman environment and the curl scripts. The signup flow will create that account as `ADMIN`, which avoids a manual DB update or restart during the demo.

## Postman

Import:

- `postman/TrustPlatform-Auth-Service.postman_collection.json`
- `postman/TrustPlatform-Local.postman_environment.json`

Recommended order:

1. Run `Demo Flow A - Happy Path` top to bottom.
2. Run `Demo Flow B - Rejection` top to bottom.

For upload requests, select [`sample-document.pdf`](/Users/yixupi/VSCode/trustplatform/auth-service/demo/sample-document.pdf).

## Curl scripts

Requirements:

- `curl`
- `jq`

Run:

```bash
./demo/flow-a-happy-path.sh
./demo/flow-b-rejection.sh
```

Optional environment variables:

```bash
BASE_URL=http://localhost:8080
ADMIN_EMAIL=demo-admin@example.com
ADMIN_PASSWORD=password123
USER_PASSWORD=password123
REJECT_USER_PASSWORD=password123
```

The scripts generate unique user emails automatically so you can rerun them without cleanup.
