package com.nexus.gaming.repository;

import java.util.List;
import java.util.Optional;

/**
 * Generic Repository interface defining standard CRUD operations.
 * Demonstrates OOP Generics and Interfaces.
 *
 * @param <T>  Domain entity type
 * @param <ID> Identifier type
 */
public interface Repository<T, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    boolean deleteById(ID id);
}

