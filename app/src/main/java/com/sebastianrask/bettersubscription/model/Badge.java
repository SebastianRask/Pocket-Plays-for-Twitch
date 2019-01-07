package com.sebastianrask.bettersubscription.model;

import java.util.HashMap;
import java.util.Map;

public class Badge {

    private String name;
    private Map<String, String> versions;

    public Badge(String name) {
        this.name = name;
        versions = new HashMap<>();
    }

    public void addVersion(String version, String url) {
        versions.put(version, url);
    }

    public String getVersion(String version) {
        return versions.get(version);
    }

    public Iterable<String> getVersions() {
        return versions.keySet();
    }

}
