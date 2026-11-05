# POP-net

A small Spring MVC status-message board backed by MySQL.

## Run locally

Create a local `.env` file from the example and choose passwords:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD`. Keep `.env` private;
it is ignored by Git. If the MySQL volume already exists, `DB_PASSWORD` must
match the password used when that volume was first initialized.

The local Docker setup also starts Keycloak as the OIDC identity provider.
Set (or add) `KEYCLOAK_ADMIN_PASSWORD` in `.env` before starting it if you want
to use the Keycloak admin console at `http://localhost:8180`. The imported
local realm has an application account `admin` with password `admin`; sign in
at the application's `/login` page and choose **Log in with POP identity**. This
account and the client secret in the imported realm are for local development
only, not production.

The app must be available at `http://localhost:8080` for the local Keycloak
client's redirect URI. Make sure that port is free before starting the app.
The local admin permission is granted to members of the `popnet-admins` group;
the imported `admin` account is in that group. For another account, add a user
to the group in the Keycloak admin console.

### Run from IntelliJ IDEA

Start the services using the **Start MySQL** Docker Compose run configuration.
It reads the passwords from `.env` and now starts both MySQL and Keycloak. If
IntelliJ asks, configure its Docker connection first and wait for both
containers to become healthy.

The application run configuration must also receive the application password:

1. Open **Run → Edit Configurations…** and select **TempApplication**.
2. Choose **Modify options → Environment variables**.
3. Add `DB_PASSWORD` with the same value as `DB_PASSWORD` in `.env` (without
   quotation marks), then click **Apply** and **OK**.
4. Run **TempApplication** after MySQL is healthy.

### Run from PowerShell

Set the app password in the same PowerShell window, then start MySQL:

```powershell
$env:DB_PASSWORD = "the-same-value-as-in-.env"
$env:MYSQL_ROOT_PASSWORD = "the-same-value-as-in-.env"
docker compose up -d --wait
```

Keep `DB_PASSWORD` set in that window while starting the application. Start the
app after both MySQL and Keycloak are healthy:

```powershell
.\mvnw.cmd spring-boot:run
```

### Run from bash, zsh or any Unix shell

One command does it all:

```sh
./run.sh
```

It checks for Java 21 and Docker. If either is missing or Docker is not
running, it asks before installing or starting it with `sudo`. On the first
run it copies `.env.example` to `.env` with random passwords. Then it starts
MySQL and Keycloak and waits until both are healthy. It requires port 8080 for
the app's registered OIDC redirect URI. Stop the app with Ctrl+C; MySQL and
Keycloak keep running in the background until you run `docker compose down`.

To do the same steps by hand:

```sh
cp .env.example .env    # then set the two passwords
set -a; source .env; set +a
docker compose up -d --wait
./mvnw spring-boot:run
```

Open `http://localhost:8080`. Port 8080 must be free for local OIDC login; if
it is occupied, stop the other service before running the app.

The home page is the public status page, and every service has its own page
at `http://localhost:8080/services/<name>`, for example `/services/pop-print-portal`.
Both are Thymeleaf templates in the `dk.pop.status.temp.view.status` package.
They show every service in the `services` table that the ping service can
check, sorted into network and servers and web services. The status, uptime, history strip
and incidents come from the state changes the ping service writes to
`status_logs`. Response times come from the `response_times` table, which gets
one row per successful web check and forgets rows older than 15 days. A
service page also says when the service was last checked.

### Example data

To see the pages full of made-up history instead, for a demo or while working
on the design, put the line `EXAMPLE_DATA=true` in `.env` and restart the app.
New `.env` files already have it, commented out with `#`, so removing the `#`
is enough. Put the `#` back to go back to real data.

For a single run without editing `.env`:

```sh
EXAMPLE_DATA=true ./run.sh
```

In IntelliJ, add `EXAMPLE_DATA` with the value `true` to the environment
variables of **TempApplication**, next to `DB_PASSWORD`.

Icons come from [Lucide](https://lucide.dev) (ISC license, see
`THIRD_PARTY_NOTICES.md`) and live in
`src/main/resources/templates/fragments/icons.html`. To add one, copy its paths
from lucide.dev into a new `th:case` there, then use it with
`<svg th:replace="~{fragments/icons :: icon('name')}"></svg>`.

Announcements are posted and deleted at `http://localhost:8080/messages` and
show on the home page. The MySQL service stores its data in a Docker volume
named `mysql-data`.
To stop the service without deleting its data, run `docker compose down`.
To delete the database and all its messages and initialize it again with new
passwords, run `docker compose down --volumes`.

By default, the application connects to `jdbc:mysql://localhost:3307/popnet`
as user `popnet`. Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through
environment variables to connect to a different MySQL server. The schema script
in `src/main/resources/migrations` runs when the application starts.

Status changes can send emails to the recipients configured under the
**Email notifications** tab in the admin UI. Add the SMTP account that should
send notifications to `.env` as `SMTP_USERNAME`, and its SMTP credential as
`SMTP_PASSWORD`. Configure `SMTP_HOST` and `SMTP_PORT` for the chosen provider.
The sender account is separate from the recipients managed in the admin UI.
Use `EMAIL_NOTIFICATIONS_ENABLED=false` to disable notifications.

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`.

## Admin diagnostics and public outage notes

Log in at `/admin` (development account: `admin` / `admin`), open **Service
diagnostics**, and select a service. The detail page shows its latest recorded
status, diagnostic message, and up to 50 recent status records. Messages are
recorded on the first check and subsequent status changes, rather than every
check.

On the same page, use **Public outage note** to write an explanation for
visitors, then click **Save public note**. Notes accept up to 200 characters.
Saving again replaces the service's current note; **Remove public note**
deletes it. Notes stay visible after recovery until edited or removed.

Published notes appear under **Service updates** on the homepage and link to
the service's public page at `/services/id/<id>`. They use database data and
remain visible with `EXAMPLE_DATA=false`. Monitoring targets and technical
diagnostics are available only on the admin pages.
