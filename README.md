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

Open `http://localhost:8080`. Messages can be added and deleted from the home
page. The MySQL service stores its data in a Docker volume named `mysql-data`.
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
