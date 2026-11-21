package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;

import java.util.List;

/** Generic storage abstraction: callers depend on this, not on JSON or files. */
public interface Repository<T> {
    List<T> findAll() throws StorageException;

    void saveAll(List<T> items) throws StorageException;
}
