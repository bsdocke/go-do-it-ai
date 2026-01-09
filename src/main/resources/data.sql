-- Using MERGE to ensure statuses exist but avoiding duplicates/errors on restart
MERGE INTO status (name, priority) KEY(name) VALUES ('Analysis', 1);
MERGE INTO status (name, priority) KEY(name) VALUES ('Ready', 2);
MERGE INTO status (name, priority) KEY(name) VALUES ('In Progress', 3);
MERGE INTO status (name, priority) KEY(name) VALUES ('Review', 4);
MERGE INTO status (name, priority) KEY(name) VALUES ('Testing', 5);
MERGE INTO status (name, priority) KEY(name) VALUES ('Staged', 6);
MERGE INTO status (name, priority) KEY(name) VALUES ('Complete', 7);
