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

To do the same steps by hand:

```sh
cp .env.example .env    # then set the two passwords
set -a; source .env; set +a
docker compose up -d --wait
./mvnw spring-boot:run
```

Open `http://localhost:8080`. If port 8080 is already taken, the app starts on
the next free port (8081, 8082, ...) and says so in the log. Set `SERVER_PORT`
to start from a different port.

The home page is the public status page, and every service has its own page
at `http://localhost:8080/services/<name>`, for example `/services/pop-print`.
Both are Thymeleaf templates filled from example data in the
`dk.pop.status.temp.view.example` package, which makes up two weeks of
incidents per service. Real data replaces that package later.

Icons come from [Lucide](https://lucide.dev) (ISC license, see
`THIRD_PARTY_NOTICES.md`) and live in
`src/main/resources/templates/fragments/icons.html`. To add one, copy its paths
from lucide.dev into a new `th:case` there, then use it with
`<svg th:replace="~{fragments/icons :: icon('name')}"></svg>`.

Status notes can be created, updated, and deleted at
`http://localhost:8080/notes`. Each note belongs to a service. The MySQL
service stores its data in a Docker volume named `mysql-data`.
To stop the service without deleting its data, run `docker compose down`.
To delete the database and all its notes and initialize it again with new
passwords, run `docker compose down --volumes`.

By default, the application connects to `jdbc:mysql://localhost:3307/popnet`
as user `popnet`. Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through
environment variables to connect to a different MySQL server. The schema script
in `src/main/resources/migrations` runs when the application starts.

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`.
