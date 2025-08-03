package org.bxteam.quark.resource;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for locating and converting between different resource representations.
 *
 * <p>This class provides a unified way to work with resources that can be represented
 * as URLs, URIs, file paths, or File objects. It handles the conversion between these
 * formats while providing proper error handling.</p>
 */
public final class ResourceLocator {
    private final URL url;

    private ResourceLocator(@NotNull URL url) {
        this.url = requireNonNull(url, "URL cannot be null");
    }

    /**
     * Gets the URL representation of this resource.
     *
     * @return the URL
     */
    @NotNull
    public URL toURL() {
        return url;
    }

    /**
     * Converts this resource to a Path.
     *
     * @return the Path representation
     * @throws ResourceException if the conversion fails
     */
    @NotNull
    public Path toPath() {
        return toFile().toPath();
    }

    /**
     * Converts this resource to a File.
     *
     * @return the File representation
     * @throws ResourceException if the conversion fails
     */
    @NotNull
    public File toFile() {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new ResourceException("Failed to convert URL to File: " + url, e);
        }
    }

    /**
     * Gets the URI representation of this resource.
     *
     * @return the URI
     * @throws ResourceException if the conversion fails
     */
    @NotNull
    public URI toURI() {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new ResourceException("Failed to convert URL to URI: " + url, e);
        }
    }

    /**
     * Creates a ResourceLocator from a URI string.
     *
     * @param uri the URI string
     * @return a new ResourceLocator
     * @throws ResourceException if the URI is invalid
     * @throws NullPointerException if uri is null
     */
    @NotNull
    public static ResourceLocator fromURI(@NotNull String uri) {
        requireNonNull(uri, "URI cannot be null");

        try {
            return new ResourceLocator(URI.create(uri).toURL());
        } catch (MalformedURLException e) {
            throw new ResourceException("Invalid URI: " + uri, e);
        } catch (IllegalArgumentException e) {
            throw new ResourceException("Malformed URI: " + uri, e);
        }
    }

    /**
     * Creates a ResourceLocator from a URL.
     *
     * @param url the URL
     * @return a new ResourceLocator
     * @throws NullPointerException if url is null
     */
    @NotNull
    public static ResourceLocator fromURL(@NotNull URL url) {
        return new ResourceLocator(url);
    }

    /**
     * Creates a ResourceLocator from a File.
     *
     * @param file the file
     * @return a new ResourceLocator
     * @throws ResourceException if the conversion fails
     * @throws NullPointerException if file is null
     */
    @NotNull
    public static ResourceLocator fromFile(@NotNull File file) {
        requireNonNull(file, "File cannot be null");

        try {
            return new ResourceLocator(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new ResourceException("Failed to convert File to URL: " + file, e);
        }
    }

    /**
     * Creates a ResourceLocator from a Path.
     *
     * @param path the path
     * @return a new ResourceLocator
     * @throws ResourceException if the conversion fails
     * @throws NullPointerException if path is null
     */
    @NotNull
    public static ResourceLocator fromPath(@NotNull Path path) {
        requireNonNull(path, "Path cannot be null");
        return fromFile(path.toFile());
    }

    /**
     * Checks if this resource represents a local file.
     *
     * @return true if this is a file URL
     */
    public boolean isFile() {
        return "file".equals(url.getProtocol());
    }

    /**
     * Checks if this resource exists (only works for file URLs).
     *
     * @return true if the resource exists, false if it doesn't or if it's not a file URL
     */
    public boolean exists() {
        if (!isFile()) {
            return false;
        }

        try {
            return toFile().exists();
        } catch (ResourceException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ResourceLocator that = (ResourceLocator) obj;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public String toString() {
        return url.toString();
    }

    /**
     * Exception thrown when resource operations fail.
     */
    public static class ResourceException extends RuntimeException {
        public ResourceException(String message) {
            super(message);
        }

        public ResourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
