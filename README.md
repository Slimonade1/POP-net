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

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`.

## Server monitoring (first version)

The backend can run administrator-provided scripts and save every completed check
to MySQL. The included `scripts/check-ping.sh` checks one server using Linux
`ping` from iputils. It sends three packets with a six-second deadline.
Run the app on a Linux machine with Bash and iputils installed for this script.
Custom scripts can use other commands available on the application host.

From the repository root, create your external configuration:

```sh
cp monitoring.example.yaml monitoring.yaml
```

Edit `monitoring.yaml` and replace `127.0.0.1` with the server's IP address or
hostname. The example checks localhost every 60 seconds with a ten-second timeout.
Keep `id` stable when renaming a service so its history stays together. Add more
entries under `monitoring.services` to check more servers, or use `enabled: false`
to disable an entry. Restart the application after editing the file.

Start the application as usual with `./run.sh`. It automatically imports
`monitoring.yaml` from the working directory. If the file is absent, no checks run.
The local file is ignored by Git. To use a file elsewhere, set
`SPRING_CONFIG_ADDITIONAL_LOCATION=file:/absolute/path/monitoring.yaml`.
Command paths are resolved from the application's working directory, so use
absolute script paths when running outside the repository root.

Checks begin after startup. A single worker runs them sequentially; each service's
interval is measured from the end of its previous attempt. A slow check can delay
other checks. Run one application instance for this early version to avoid duplicate
checks. There are no retries yet.

### Script contract and stored results

Commands are configured as an argument list and executed directly, without shell
string interpolation. Scripts must run in the foreground and finish within the
configured timeout. Only administrators should edit these commands: they execute
with the application's operating-system permissions.

| Script exit code | Stored status | Meaning |
| --- | --- | --- |
| 0 | `healthy` | Check succeeded |
| 1 | `sick` | Degraded; the ping wrapper received only some replies |
| 2 | `down` | Check failed; the ping wrapper received no replies |
| Anything else | `error` | The check itself failed to execute correctly |

A runner timeout or failure to start also produces `error`, rather than claiming
the monitored server is down. The ping wrapper translates iputils exit codes into
this contract. Ping establishes network reachability, not application health.

Each result includes a UTC `checked_at` timestamp, `duration_ms`, `exit_code`,
`stdout`, `stderr`, `check_error`, and `output_truncated`. Output is decoded as UTF-8
text and capped at 64 KiB per stream; the rest is drained and discarded. Execution
duration is not network latency: ping's packet timings remain in its raw output.
Script failures are saved just like successful checks. If MySQL is unavailable,
the application logs the save failure and continues; there is no durable retry queue.
Stopping the application interrupts the active check without saving a partial result.

The startup schema upgrade preserves existing rows, adds the monitoring columns
and indexes, and creates the missing `status_message` table for the message board.
Existing logs retain their original date/time values; their new columns remain null.
Configured services get a unique `services.monitor_key` based on their YAML ID.
Legacy service rows without a key are left as they are.

Inspect recent checks in MySQL:

```sql
SELECT s.name, l.checked_at, l.stats, l.duration_ms, l.exit_code,
       l.stdout, l.stderr, l.check_error, l.output_truncated
FROM status_logs l
JOIN services s ON s.id = l.service_id
WHERE l.checked_at IS NOT NULL
ORDER BY l.checked_at DESC
LIMIT 20;
```

The public dashboard still displays example data. This version collects results
only; dashboard integration, admin login, and protected error details are subsequent
work. Raw script output is not exposed through a web endpoint.
