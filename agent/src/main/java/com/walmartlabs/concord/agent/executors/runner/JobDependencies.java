package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.project.InternalConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.dependencymanager.DependencyManager.MAVEN_SCHEME;

public class JobDependencies {

    private static final Logger log = LoggerFactory.getLogger(JobDependencies.class);

    private static final String MVN_VERSION_PATTERN = ".*:[\\d]+(?:\\.\\d+)*(?:-\\w*)?$";

    public static Collection<URI> get(RunnerJob job) throws ExecutionException {
        Collection<URI> uris = getDependencyUris(job);
        Map<String, String> versions = getDependencyVersions(job);
        if (versions.isEmpty()) {
            return uris;
        }

        return updateVersions(uris, versions);
    }

    private static Collection<URI> updateVersions(Collection<URI> uris, Map<String, String> versions) {
        List<URI> result = new ArrayList<>();
        for (URI item : uris) {
            String scheme = item.getScheme();
            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                String id = item.getAuthority();
                if (withoutVersion(id)) {
                    result.add(URI.create(MAVEN_SCHEME + "://" + id + ":" + assertVersion(id, versions)));
                }
            } else {
                result.add(item);
            }
        }
        return result;

    }

    @SuppressWarnings("unchecked")
    private static Collection<URI> getDependencyUris(RunnerJob job) throws ExecutionException {
        try {
            Map<String, Object> m = job.getCfg();
            Collection<String> deps = (Collection<String>) m.get(InternalConstants.Request.DEPENDENCIES_KEY);
            return normalizeUrls(deps);
        } catch (URISyntaxException | IOException e) {
            throw new ExecutionException("Error while reading the list of dependencies: " + e.getMessage(), e);
        }
    }

    private static Collection<URI> normalizeUrls(Collection<String> urls) throws IOException, URISyntaxException {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptySet();
        }

        Collection<URI> result = new HashSet<>();

        for (String s : urls) {
            URI u = new URI(s);
            String scheme = u.getScheme();

            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                result.add(u);
                continue;
            }

            if (scheme == null || scheme.trim().isEmpty()) {
                throw new IOException("Invalid dependency URL. Missing URL scheme: " + s);
            }

            if (s.endsWith(".jar")) {
                result.add(u);
                continue;
            }

            URL url = u.toURL();
            while (true) {
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    URLConnection conn = url.openConnection();
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection httpConn = (HttpURLConnection) conn;
                        httpConn.setInstanceFollowRedirects(false);

                        int code = httpConn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                                code == HttpURLConnection.HTTP_MOVED_PERM ||
                                code == HttpURLConnection.HTTP_SEE_OTHER ||
                                code == 307) {

                            String location = httpConn.getHeaderField("Location");
                            url = new URL(location);
                            log.info("normalizeUrls -> using: {}", location);

                            continue;
                        }

                        u = url.toURI();
                    } else {
                        log.warn("normalizeUrls -> unexpected connection type: {} (for {})", conn.getClass(), s);
                    }
                }

                break;
            }

            result.add(u);
        }

        return result;
    }

    private static Map<String, String> getDependencyVersions(RunnerJob job) throws ExecutionException {
        Path workDir = job.getPayloadDir();
        Path pluginsFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.DEPENDENCY_VERSIONS_FILE_NAME);

        if (!Files.exists(pluginsFile)) {
            return Collections.emptyMap();
        }

        try (InputStream is = Files.newInputStream(pluginsFile)) {
            Map<String, String> result = new HashMap<>();
            Properties p = new Properties();
            p.load(is);
            for (String name : p.stringPropertyNames()) {
                result.put(name, p.getProperty(name));
            }
            return result;
        } catch (IOException e) {
            throw new ExecutionException("Error while reading default dependency versions: " + e.getMessage(), e);
        }
    }

    private static boolean withoutVersion(String s) {
        return !s.matches(MVN_VERSION_PATTERN);
    }

    private static String assertVersion(String dep, Map<String, String> versions) {
        String version = versions.get(dep);
        if (version != null) {
            return version;
        }
        throw new IllegalArgumentException("Unofficial plugin '" + dep + "': version is required");
    }
}
