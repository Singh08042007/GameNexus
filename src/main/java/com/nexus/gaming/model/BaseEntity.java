package com.nexus.gaming.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base entity providing common identity and audit timestamps.
 * Demonstrates OOP Abstraction, Encapsulation, and Inheritance.
 */
public abstract class BaseEntity {

    private String id;
    private String createdAt;
    private String updatedAt;

    protected BaseEntity() {
        this.id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected BaseEntity(String id, String createdAt, String updatedAt) {
        this.id = (id != null && !id.trim().isEmpty()) ? id : UUID.randomUUID().toString();
        String now = Instant.now().toString();
        this.createdAt = (createdAt != null && !createdAt.trim().isEmpty()) ? createdAt : now;
        this.updatedAt = (updatedAt != null && !updatedAt.trim().isEmpty()) ? updatedAt : now;
    }

    public final String getId() {
        return id;
    }

    public final void setId(String id) {
        this.id = id;
    }

    public final String getCreatedAt() {
        return createdAt;
    }

    public final void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public final String getUpdatedAt() {
        return updatedAt;
    }

    public final void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public final void touch() {
        this.updatedAt = Instant.now().toString();
    }

    public abstract String getEntityType();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
