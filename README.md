# POP-net

A small Spring MVC status-message board backed by MySQL.

## Run locally

Start the MySQL database with Docker Compose. In PowerShell, set the database
passwords and start the database:

```powershell
$env:DB_PASSWORD = "choose-a-local-app-password"
$env:MYSQL_ROOT_PASSWORD = "choose-a-local-root-password"
docker compose up -d --wait
```

Keep `DB_PASSWORD` set in the same terminal when starting the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. Messages can be added and deleted from the home
page. The MySQL service stores its data in a Docker volume named `mysql-data`.
To stop the service without deleting its data, run `docker compose down`.

By default, the application connects to `jdbc:mysql://localhost:3306/popnet`
as user `popnet`. Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through
environment variables to connect to a different MySQL server. The schema script
in `src/main/resources/migrations` runs when the application starts.

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`.
