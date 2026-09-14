CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    display_name  VARCHAR(120) NOT NULL,
    avatar_url    VARCHAR(500),
    role          VARCHAR(20) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL,
    provider_id   VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
CREATE UNIQUE INDEX users_provider_key ON users (auth_provider, provider_id) WHERE provider_id IS NOT NULL;

CREATE TABLE assignments (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    created_by  BIGINT NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT assignments_dates_check CHECK (end_at > start_at)
);
CREATE INDEX assignments_end_at_idx ON assignments (end_at);

CREATE TABLE projects (
    id            BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    submitter_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT NOT NULL,
    showcase_url  VARCHAR(500) NOT NULL,
    git_repo_url  VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX projects_assignment_id_idx ON projects (assignment_id);
CREATE INDEX projects_submitter_id_idx ON projects (submitter_id);

CREATE TABLE tech_labels (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(40) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX tech_labels_name_lower_key ON tech_labels (lower(name));

CREATE TABLE project_tech_labels (
    project_id    BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    tech_label_id BIGINT NOT NULL REFERENCES tech_labels (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, tech_label_id)
);

CREATE TABLE votes (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    voter_id   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT votes_project_voter_key UNIQUE (project_id, voter_id)
);
CREATE INDEX votes_project_id_idx ON votes (project_id);
