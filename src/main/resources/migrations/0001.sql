-- Create the core services table
CREATE TABLE IF NOT EXISTS services (
    id INT AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(511) NOT NULL, -- Holds the connection URLs (icmp:// or https://)
    PRIMARY KEY (id)
);

-- Create the dedicated status logging timeline table
CREATE TABLE IF NOT EXISTS status_log (
    id BIGINT AUTO_INCREMENT,
    service_id INT NOT NULL,
    status VARCHAR(50) NOT NULL,       -- "healthy", "sick", or "down"
    time TIME NOT NULL,                --
    date DATE NOT NULL,                --
    message VARCHAR(255),              --
    delay_ms INT NOT NULL,             --
    PRIMARY KEY (id),
    CONSTRAINT fk_status_log_service 
        FOREIGN KEY (service_id) 
        REFERENCES services(id) 
        ON DELETE CASCADE
);

-- Filling database with known POP infrastructure target list
INSERT IGNORE INTO services (id, name, description) VALUES 
-- Network Infrastructure
(1, 'Management VPN 1', 'icmp://10.255.0.1'),
(2, 'Management VPN 2', 'icmp://10.255.0.3'),
(3, 'Management VPN Gateway', 'icmp://177.28.242.91'),
-- Servers
(4, 'Server Jane', 'icmp://jane.pop.dk'),
(5, 'Server John Core', 'icmp://john.pop.dk'),
(6, 'Server John Alt-IP', 'icmp://82.211.200.8'),
(7, 'Server John IPv6', 'icmp://[2a03:19c0:8005:6000:1]'),
-- Web Services
(8, 'POP Booking Heartbeat', 'https://pop.dk'),
(9, 'POP Main Website', 'https://pop.dk'),
(10, 'POP WordPress Login', 'https://pop.dk'),
(11, 'POP Web Public Domain', 'https://pop.dk'),
(12, 'Network Monitor Service', 'https://pop.dk'),
(13, 'POP Print Portal', 'https://pop.dk'),
(14, 'Self-service Redirection', 'https://pop.dk'),
(15, 'Reset Password Gateway', 'https://k-net.dk'),
(16, 'Password Manager Config', 'https://pop.dk'),
(17, 'Password Manager Alive', 'https://pop.dk'),
(18, 'Netgrp Wiki Knowledgebase', 'https://pop.dk'),