package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Isolated class loader that extends URLClassLoader for loading dependencies
 * in an isolated environment.
 *
 * <p>This class loader uses the system class loader's parent as its parent
 * to provide isolation from application classes while maintaining access
 * to core Java classes.</p>
 */
public class IsolatedClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * Creates a new isolated class loader for the given URLs.
     *
     * @param urls the URLs to add to the classpath
     */
    public IsolatedClassLoader(@NotNull URL... urls) {
        super(requireNonNull(urls, "urls"), ClassLoader.getSystemClassLoader().getParent());
    }

    /**
     * Adds a URL to the classpath.
     *
     * @param url the URL to add
     */
    @Override
    public void addURL(@NotNull URL url) {
        super.addURL(requireNonNull(url, "url"));
    }

    /**
     * Adds a path to the classpath.
     *
     * @param path the path to add
     */
    public void addPath(@NotNull Path path) {
        try {
            addURL(requireNonNull(path, "path").toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Defines and loads a class.
     *
     * @param name The expected binary name of the class
     * @param classBytes An {@link InputStream} which provides the class bytes. It will be automatically closed by this
     *                   method after being read.
     * @return The defined class
     * @throws IOException If an exception occurs while reading the provided {@link InputStream}
     * @throws ClassFormatError If the bytes provided by the {@link InputStream} doesn't contain valid class
     * @see ClassLoader#defineClass(String, byte[], int, int)
     */
    public Class<?> defineClass(@NotNull String name, @NotNull InputStream classBytes) throws IOException, ClassFormatError {
        byte[] bytes = readAllBytes(classBytes);
        return super.defineClass(name, bytes, 0, bytes.length);
    }

    /**
     * Reads all bytes, and safely closes {@link InputStream}. Always has buffer length 4 KB.
     *
     * @return The all bytes of {@link InputStream}
     * @param inputStream {@link InputStream} that would be read
     * @throws IOException If {@link InputStream} has been closed, or bytes cannot be read, or other I/O error occurs.
     * @see InputStream#read(byte[], int, int)
     */
    private static byte[] readAllBytes(@NotNull InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400;
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                    outputStream.write(buf, 0, readLen);

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null) inputStream.close();
            else try {
                inputStream.close();
            } catch (IOException e) {
                exception.addSuppressed(e);
            }
        }
    }
}
