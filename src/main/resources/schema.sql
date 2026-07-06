-- DROP SHOT Matching database schema
-- Spring Boot runs this file on startup before JPA validates the entity mapping.

CREATE SCHEMA IF NOT EXISTS dropshot;

CREATE TABLE IF NOT EXISTS dropshot.members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    gender VARCHAR(10) NOT NULL DEFAULT '미지정',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_member_name_grade UNIQUE (name, grade)
);

CREATE TABLE IF NOT EXISTS dropshot.events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    games_per_player INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    court_count INT NOT NULL DEFAULT 4,
    game_started BOOLEAN NOT NULL DEFAULT FALSE,
    court_assignments_json TEXT NULL,
    court_names_json TEXT NULL,
    waiting_since_json TEXT NULL,
    resting_participant_ids_json TEXT NULL,
    exited_participant_ids_json TEXT NULL
);

ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP NULL;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS court_count INT NOT NULL DEFAULT 4;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS game_started BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS court_assignments_json TEXT NULL;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS court_names_json TEXT NULL;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS waiting_since_json TEXT NULL;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS resting_participant_ids_json TEXT NULL;
ALTER TABLE dropshot.events ADD COLUMN IF NOT EXISTS exited_participant_ids_json TEXT NULL;
UPDATE dropshot.events SET court_assignments_json = '{}' WHERE court_assignments_json IS NULL;
UPDATE dropshot.events SET court_names_json = '{}' WHERE court_names_json IS NULL;
UPDATE dropshot.events SET waiting_since_json = '{}' WHERE waiting_since_json IS NULL;
UPDATE dropshot.events SET resting_participant_ids_json = '[]' WHERE resting_participant_ids_json IS NULL;
UPDATE dropshot.events SET exited_participant_ids_json = '[]' WHERE exited_participant_ids_json IS NULL;

CREATE TABLE IF NOT EXISTS dropshot.participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    member_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    gender VARCHAR(10) NOT NULL DEFAULT '미지정',
    game_count INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_participants_event FOREIGN KEY (event_id) REFERENCES dropshot.events (id),
    CONSTRAINT fk_participants_member FOREIGN KEY (member_id) REFERENCES dropshot.members (id),
    INDEX idx_participants_event (event_id),
    INDEX idx_participants_member (member_id)
);

ALTER TABLE dropshot.participants ADD COLUMN IF NOT EXISTS game_count INT NOT NULL DEFAULT 0;
ALTER TABLE dropshot.members ADD COLUMN IF NOT EXISTS gender VARCHAR(10) NOT NULL DEFAULT '미지정';
ALTER TABLE dropshot.participants ADD COLUMN IF NOT EXISTS gender VARCHAR(10) NOT NULL DEFAULT '미지정';

CREATE TABLE IF NOT EXISTS dropshot.partner_pairs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    participant_a_id BIGINT NOT NULL,
    participant_b_id BIGINT NOT NULL,
    CONSTRAINT fk_partner_pairs_event FOREIGN KEY (event_id) REFERENCES dropshot.events (id),
    CONSTRAINT fk_partner_pairs_participant_a FOREIGN KEY (participant_a_id) REFERENCES dropshot.participants (id),
    CONSTRAINT fk_partner_pairs_participant_b FOREIGN KEY (participant_b_id) REFERENCES dropshot.participants (id),
    INDEX idx_partner_pairs_event (event_id),
    INDEX idx_partner_pairs_participant_a (participant_a_id),
    INDEX idx_partner_pairs_participant_b (participant_b_id)
);

CREATE TABLE IF NOT EXISTS dropshot.completed_court_games (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    court_no INT NOT NULL,
    court_name VARCHAR(100) NOT NULL DEFAULT '',
    completed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_completed_court_games_event FOREIGN KEY (event_id) REFERENCES dropshot.events (id),
    INDEX idx_completed_court_games_event (event_id, completed_at)
);

ALTER TABLE dropshot.completed_court_games ADD COLUMN IF NOT EXISTS court_name VARCHAR(100) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS dropshot.completed_court_game_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    completed_court_game_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    seat_no INT NOT NULL,
    CONSTRAINT fk_completed_court_game_members_game FOREIGN KEY (completed_court_game_id) REFERENCES dropshot.completed_court_games (id),
    CONSTRAINT fk_completed_court_game_members_participant FOREIGN KEY (participant_id) REFERENCES dropshot.participants (id),
    INDEX idx_completed_court_game_members_game (completed_court_game_id),
    INDEX idx_completed_court_game_members_participant (participant_id)
);

CREATE TABLE IF NOT EXISTS dropshot.group_matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    group_no INT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_group_matches_event FOREIGN KEY (event_id) REFERENCES dropshot.events (id),
    INDEX idx_group_matches_event_round (event_id, round_no, group_no)
);

CREATE TABLE IF NOT EXISTS dropshot.group_match_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_match_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    seat_no INT NOT NULL,
    CONSTRAINT fk_group_match_members_group FOREIGN KEY (group_match_id) REFERENCES dropshot.group_matches (id),
    CONSTRAINT fk_group_match_members_participant FOREIGN KEY (participant_id) REFERENCES dropshot.participants (id),
    INDEX idx_group_match_members_group (group_match_id),
    INDEX idx_group_match_members_participant (participant_id)
);
