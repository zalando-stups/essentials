# ESSENTIALS

ESSENTIALS is the repository for resource types and OAuth 2.0 scopes in the STUPS ecosystem.


Variable                | Mandatory? | Default                       | Description
----------------------- | ---------- | ----------------------------- | -----------
DB_SUBNAME              | yes        | `//localhost:5432/essentials` | JDBC connection information of your database.
DB_USER                 | yes        | `postgres`                    | Database user.
DB_PASSWORD             | yes        | `postgres`                    | Database password.

Example:

```
$ docker run -it \
    -e DB_USER=essentials \
    -e DB_PASSWORD=essentials123 \
    stups/essentials
```
