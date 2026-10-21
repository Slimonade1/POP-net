# POP-net

A small Spring MVC status-message board backed by an H2 file database.

Run the application with `.\mvnw.cmd spring-boot:run`, then open
`http://localhost:8080`. Messages can be added and deleted from the home page.
The H2 console is available at `http://localhost:8080/h2-console`; use JDBC URL
`jdbc:h2:file:./runtime/popnet`, user `sa`, and a blank password. Database files
are stored in `runtime` relative to the application working directory and are
excluded from Git.

Application code is organized into `domain`, `repository`, `services`, `utils`,
and `view` packages. Thymeleaf templates live in
`src/main/resources/templates`. H2 schema scripts are kept in
`src/main/resources/migrations` and run when the database starts.
