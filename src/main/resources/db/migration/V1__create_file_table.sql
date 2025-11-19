CREATE TABLE files(
    id BINARY(16) PRIMARY KEY NOT NULL DEFAULT(uuid_to_bin(uuid())),
    original_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL UNIQUE,
    type VARCHAR(255) NOT NULL,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);