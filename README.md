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

### Run from IntelliJ IDEA

Start the database using the **Start MySQL** Docker Compose run configuration.
It reads the passwords from `.env`. If IntelliJ asks, configure its Docker
connection first and wait for the MySQL container to become healthy.

Run **TempApplication** after MySQL is healthy. Spring reads the project-root
`.env` file automatically, so the app uses the same `DB_PASSWORD` as Docker
Compose without copying the password into an IntelliJ run configuration.

### Run from PowerShell

Set the app password in the same PowerShell window, then start MySQL:

```powershell
$env:DB_PASSWORD = "the-same-value-as-in-.env"
$env:MYSQL_ROOT_PASSWORD = "the-same-value-as-in-.env"
docker compose up -d --wait
```

Keep `DB_PASSWORD` set in that window while starting the application:

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
MySQL, waits until the database is healthy, and runs the app on port 8080, or
the next free port if 8080 is taken. Stop the app with Ctrl+C; MySQL keeps
running in the background until you run `docker compose down`.

To start only MySQL for an IntelliJ run, load `.env` and start the Compose
service. Then launch **TempApplication** from IntelliJ. Do not run `./run.sh`
at the same time; it also starts the Java application.

To do the full startup from the terminal:

```sh
cp .env.example .env    # then set the two passwords
set -a; source .env; set +a
docker compose up -d --wait
./mvnw spring-boot:run
```

Open `http://localhost:8080`. If port 8080 is already taken, the app starts on
the next free port (8081, 8082, ...) and says so in the log. Set `SERVER_PORT`
to start from a different port.

The home page shows administrator-written notes under **Service updates**.
These notes come from the database and link to real services at
`/services/id/<id>`. The status charts and incident history below are still an
explicitly labelled example preview from `dk.pop.status.temp.view.example`.
Example service pages, such as `/services/pop-print`, belong to that preview.

### Turn the example data on or off

The example data is on by default. To turn it off, put the line
`EXAMPLE_DATA=false` in `.env` and restart the app. New `.env` files already
have it, commented out with `#`, so removing the `#` is enough. This hides the
example status charts and incidents; saved service notes and announcements
remain visible. Put the `#` back to turn the example data on again.

For a single run without editing `.env`:

```sh
EXAMPLE_DATA=false ./run.sh
```

In IntelliJ, add `EXAMPLE_DATA` with the value `false` to the environment
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

The log repository supports both the original `stats` / `duration_ms` columns
and the `status` / `delay_ms` columns used by newer installations. It detects
the existing names for reads and writes, so the diagnostics and public-note
features require no log-column migration. Databases already upgraded by the
earlier migration are also supported, including logs saved in either duration
column. Restart the app after changing database columns so it detects them again.

The only remaining schema upgrade in `0002` adds `services.retry_threshold`
when missing. This field stores the per-service retry settings from the
existing retry-threshold feature. It preserves existing data and does nothing
on subsequent starts once the field exists.

Admins can open **Service diagnostics** at `/admin/services` to see the latest
recorded status and diagnostic message for each database service. Select a
service to see its monitoring target and up to 50 recent status records, newest
first. These pages require an admin login and use real database logs even when
the public status pages show example data. The monitor records its first result
and subsequent status changes, so the displayed error is the saved diagnostic
from that event, not necessarily the latest check. Stored diagnostics are
currently limited to 200 characters. The pages use basic HTML within the
existing admin layout so their presentation can be developed separately.

On the same service detail page, admins can create or edit a **Public outage
note**, or remove it. The form accepts up to 511 characters and preserves line
breaks. Each service has one current note, stored in the existing `status_notes`
table and associated by service ID. Saving replaces that service's previous
note. The note remains public after recovery until an admin edits or removes
it; this feature does not archive notes against individual incidents. Technical
diagnostics and monitoring targets remain on the admin pages.

No schema migration is needed. Saves and removals lock the service row inside
a transaction to keep concurrent requests from creating multiple current notes.

The diagnostics tests use an isolated H2 database and rendered MVC pages. Run
them without starting MySQL:

```sh
./mvnw -Dtest=StatusLogRepositoryTest,StatusLogControllerTest test
```

The public-note tests cover persistence, editing/removal, validation, HTML
escaping, admin authorization, CSRF protection, and anonymous public access:

```sh
./mvnw -Dtest=StatusNoteUtilsTest,StatusNoteIntegrationTest test
```

Status changes can send emails to the recipients configured under the
**Email notifications** tab in the admin UI. Add the SMTP account that should
send notifications to `.env` as `SMTP_USERNAME`, and its SMTP credential as
`SMTP_PASSWORD`. Configure `SMTP_HOST` and `SMTP_PORT` for the chosen provider.
The sender account is separate from the recipients managed in the admin UI.
Use `EMAIL_NOTIFICATIONS_ENABLED=false` to disable notifications.

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`.
