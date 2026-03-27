package com.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "releases")
public class Release {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_release_id", nullable = false, unique = true)
    private String externalReleaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "version_major", nullable = false)
    private Integer versionMajor;

    @Column(name = "version_minor", nullable = false)
    private Integer versionMinor;

    @Column(name = "version_patch", nullable = false)
    private Integer versionPatch;

    @Column(name = "version_raw", nullable = false)
    private String versionRaw;

    @Column(name = "version_prerelease")
    private String versionPrerelease;

    @Column(name = "version_prerelease_sort_key")
    private String versionPrereleaseSortKey;

    @Column(name = "oci_reference", nullable = false)
    private String ociReference;

    @Column(name = "release_date", nullable = false)
    private Instant releaseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Release() {
    }

    public Release(String externalReleaseId, Application application,
                   int versionMajor, int versionMinor, int versionPatch, String versionRaw,
                   String ociReference, Instant releaseDate) {
        this(externalReleaseId, application, versionMajor, versionMinor, versionPatch,
                versionRaw, null, null, ociReference, releaseDate);
    }

    public Release(String externalReleaseId, Application application,
                   int versionMajor, int versionMinor, int versionPatch, String versionRaw,
                   String versionPrerelease, String versionPrereleaseSortKey,
                   String ociReference, Instant releaseDate) {
        this.externalReleaseId = externalReleaseId;
        this.application = application;
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.versionPatch = versionPatch;
        this.versionRaw = versionRaw;
        this.versionPrerelease = versionPrerelease;
        this.versionPrereleaseSortKey = versionPrereleaseSortKey;
        this.ociReference = ociReference;
        this.releaseDate = releaseDate;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getExternalReleaseId() {
        return externalReleaseId;
    }

    public Application getApplication() {
        return application;
    }

    public Integer getVersionMajor() {
        return versionMajor;
    }

    public Integer getVersionMinor() {
        return versionMinor;
    }

    public Integer getVersionPatch() {
        return versionPatch;
    }

    public String getVersionRaw() {
        return versionRaw;
    }

    public String getVersionPrerelease() {
        return versionPrerelease;
    }

    public String getVersionPrereleaseSortKey() {
        return versionPrereleaseSortKey;
    }

    public String getOciReference() {
        return ociReference;
    }

    public Instant getReleaseDate() {
        return releaseDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
