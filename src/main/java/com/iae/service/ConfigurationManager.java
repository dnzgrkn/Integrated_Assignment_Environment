package com.iae.service;

import com.iae.model.Configuration;
import com.iae.persistence.ConfigurationRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigurationManager {

    private static ConfigurationManager instance;

    private final ConfigurationRepository repository;
    private final Map<String, Configuration> cache = new LinkedHashMap<>();
    private boolean loaded;

    private ConfigurationManager(ConfigurationRepository repository) {
        this.repository = repository;
    }

    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager(
                    new ConfigurationRepository(ConfigurationRepository.defaultDirectory()));
        }
        return instance;
    }

    public static synchronized void resetForTesting(ConfigurationRepository repository) {
        instance = new ConfigurationManager(repository);
    }

    public synchronized List<Configuration> getAll() {
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    public synchronized Optional<Configuration> getById(String id) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(id));
    }

    public synchronized void save(Configuration config) throws IOException {
        if (config == null || config.getId() == null || config.getId().isBlank()) {
            throw new IllegalArgumentException("Configuration must have a non-blank id");
        }
        ensureLoaded();
        repository.save(config);
        cache.put(config.getId(), config);
    }

    public synchronized boolean delete(String id) throws IOException {
        ensureLoaded();
        boolean removed = repository.delete(id);
        cache.remove(id);
        return removed;
    }

    public synchronized Configuration importConfiguration(Path source) throws IOException {
        ensureLoaded();
        Configuration imported = repository.importFromFile(source);
        cache.put(imported.getId(), imported);
        return imported;
    }

    public synchronized void exportConfiguration(Configuration config, Path destination) throws IOException {
        repository.exportToFile(config, destination);
    }

    public synchronized void refresh() throws IOException {
        cache.clear();
        for (Configuration c : repository.loadAll()) {
            cache.put(c.getId(), c);
        }
        loaded = true;
    }

    private void ensureLoaded() {
        if (!loaded) {
            try {
                refresh();
            } catch (IOException e) {
                // Treat unreadable directory as empty — first run, no configs yet.
                loaded = true;
            }
        }
    }
}
