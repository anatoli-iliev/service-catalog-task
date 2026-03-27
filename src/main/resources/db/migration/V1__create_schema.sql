CREATE TABLE applications (
    id                        BIGSERIAL       PRIMARY KEY,
    external_application_id   VARCHAR(255)    NOT NULL UNIQUE,
    name                      VARCHAR(255),
    description               TEXT,
    repository_url            VARCHAR(2048),
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE releases (
    id                              BIGSERIAL       PRIMARY KEY,
    external_release_id             VARCHAR(255)    NOT NULL UNIQUE,
    application_id                  BIGINT          NOT NULL REFERENCES applications(id),
    version_major                   INTEGER         NOT NULL,
    version_minor                   INTEGER         NOT NULL,
    version_patch                   INTEGER         NOT NULL,
    version_prerelease              VARCHAR(255),
    version_prerelease_sort_key     VARCHAR(255),
    version_raw                     VARCHAR(255)    NOT NULL,
    oci_reference                   VARCHAR(2048)   NOT NULL,
    release_date                    TIMESTAMPTZ     NOT NULL,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_releases_application_semver
    ON releases(application_id, version_major DESC, version_minor DESC, version_patch DESC, version_prerelease_sort_key);
