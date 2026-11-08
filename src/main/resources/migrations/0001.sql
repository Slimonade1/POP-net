-- ====================================================================
-- POP-NET INFRASTRUCTURE TARGET SEEDING SEQUENCE
-- Populates the schema initialized in 0000.sql
-- ====================================================================

INSERT IGNORE INTO services (id, name, description) VALUES 
-- Network Infrastructure
(1, 'Management VPN 1', 'icmp://10.255.0.1'),
(2, 'Management VPN 2', 'icmp://10.255.0.3'),
(3, 'Management VPN Gateway', 'icmp://177.28.242.91'),

-- Servers
(4, 'Server Jane', 'icmp://jane.pop.dk'),
(5, 'Server John Core', 'icmp://john.pop.dk'),
(6, 'Server John Alt-IP', 'icmp://82.211.200.8'),
(7, 'Server John IPv6', 'icmp://[2a03:19c0:8005:6000::1]'),

-- Web Services
(8, 'POP Booking Heartbeat', 'https://book.pop.dk/api/heartbeat'),
(9, 'POP Main Website 1', 'https://pop.dk'),
(10, 'POP Main Website Wordpress login', 'https://pop.dk/wp-login.php'),
(11, 'POP Web Public Domain', 'https://www.pop.dk'),
(12, 'Network Monitor Service', 'https://nms.pop.dk'),
(13, 'POP Print Portal', 'https://print.pop.dk/login'),
(14, 'Self-service Redirection', 'https://self-service.pop.dk'),
(15, 'Reset Password Gateway', 'https://reset.k-net.dk/heartbeat'),
(16, 'Password Manager Config', 'https://pass.pop.dk/api/config'),
(17, 'Password Manager Alive', 'https://pass.pop.dk/alive'),
(18, 'Netgrp Wiki Knowledgebase', 'https://wiki.pop.dk');