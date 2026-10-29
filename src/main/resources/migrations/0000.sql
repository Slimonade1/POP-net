CREATE TABLE IF NOT EXISTS services(

    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(511)

);

CREATE TABLE IF NOT EXISTS status_logs(

    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    stats ENUM('healthy', 'sick', 'down'),
    time TIME,
    date DATE,
    message VARCHAR(255),
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE

);

CREATE TABLE IF NOT EXISTS status_notes(

    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(511) NOT NULL,
    service_id BIGINT NOT NULL,
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE

);