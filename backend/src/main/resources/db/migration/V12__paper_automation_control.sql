CREATE TABLE paper_automation_control (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    stopped BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO paper_automation_control (id, stopped, updated_at)
VALUES (1, FALSE, CURRENT_TIMESTAMP);
