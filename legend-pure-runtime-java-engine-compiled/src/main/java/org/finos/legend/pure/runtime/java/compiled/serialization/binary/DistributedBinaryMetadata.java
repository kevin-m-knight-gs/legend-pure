package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DistributedBinaryMetadata
{
    private final String name;
    private final Set<String> dependencies;

    private DistributedBinaryMetadata(String name, Set<String> dependencies)
    {
        this.name = name;
        this.dependencies = dependencies;
    }

    /**
     * Get the name of this metadata.
     *
     * @return metadata name
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Get the names of the metadata that this depends on.
     *
     * @return metadata dependency names
     */
    public Set<String> getDependencies()
    {
        return this.dependencies;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DistributedBinaryMetadata))
        {
            return false;
        }
        DistributedBinaryMetadata that = (DistributedBinaryMetadata) other;
        return this.name.equals(that.name) && this.dependencies.equals(that.dependencies);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{name=\"" + this.name + "\", dependencies=" + this.dependencies + "}";
    }

    public static DistributedBinaryMetadata newMetadata(String name)
    {
        return new DistributedBinaryMetadata(DistributedMetadataHelper.validateMetadataName(name), Collections.emptySet());
    }

    public static DistributedBinaryMetadata newMetadata(String name, String... dependencies)
    {
        return newMetadata(name, Arrays.asList(dependencies));
    }

    @JsonCreator
    public static DistributedBinaryMetadata newMetadata(@JsonProperty("name") String name, @JsonProperty("dependencies") Iterable<String> dependencies)
    {
        if (dependencies == null)
        {
            return newMetadata(name);
        }

        Set<String> dependencySet = (dependencies instanceof Set) ? (Set<String>) dependencies : Sets.mutable.withAll(dependencies);
        if (dependencySet.contains(null))
        {
            throw new IllegalArgumentException("Dependencies may not contain null");
        }
        return new DistributedBinaryMetadata(DistributedMetadataHelper.validateMetadataName(name), dependencySet.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(dependencySet));
    }

    public static List<Path> writeMetadata(Path directory, DistributedBinaryMetadata... metadata) throws IOException
    {
        return writeMetadata(directory, Arrays.asList(metadata));
    }

    public static List<Path> writeMetadata(Path directory, Iterable<? extends DistributedBinaryMetadata> metadata) throws IOException
    {
        List<Path> paths = Lists.mutable.empty();
        ObjectWriter writer = getMetadataObjectWriter();
        String separator = directory.getFileSystem().getSeparator();
        UnaryOperator<String> pathTransform = "/".equals(separator) ? UnaryOperator.identity() : s -> s.replace("/", separator);
        for (DistributedBinaryMetadata m : metadata)
        {
            String relativePathString = pathTransform.apply(DistributedMetadataHelper.getMetadataDefinitionFilePath(m.getName()));
            Path filePath = directory.resolve(relativePathString);
            Files.createDirectories(filePath.getParent());
            try (BufferedWriter outputWriter = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8))
            {
                writer.writeValue(outputWriter, m);
            }
            paths.add(filePath);
        }
        return paths;
    }

    public static void writeMetadata(OutputStream stream, DistributedBinaryMetadata metadata) throws IOException
    {
        Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        try
        {
            writeMetadata(writer, metadata);
        }
        finally
        {
            writer.flush();
        }
    }

    public static void writeMetadata(Writer writer, DistributedBinaryMetadata metadata) throws IOException
    {
        ObjectWriter objectWriter = getMetadataObjectWriter();
        objectWriter.writeValue(writer, metadata);
    }

    public static DistributedBinaryMetadata readMetadata(Path file) throws IOException
    {
        try (InputStream stream = Files.newInputStream(file))
        {
            return readMetadata(stream);
        }
    }

    public static DistributedBinaryMetadata readMetadata(InputStream stream) throws IOException
    {
        ObjectReader objectReader = getMetadataObjectReader();
        return objectReader.readValue(stream);
    }

    public static DistributedBinaryMetadata readMetadata(Reader reader) throws IOException
    {
        ObjectReader objectReader = getMetadataObjectReader();
        return objectReader.readValue(reader);
    }

    public static List<DistributedBinaryMetadata> loadMetadata(ClassLoader classLoader, String... metadataNames)
    {
        return loadMetadata(classLoader, Arrays.asList(metadataNames));
    }

    public static List<DistributedBinaryMetadata> loadMetadata(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        Set<String> visited = Sets.mutable.empty();
        List<DistributedBinaryMetadata> metadataList = Lists.mutable.empty();
        Deque<String> toLoad = Iterate.addAllTo(metadataNames, new ArrayDeque<>());
        ObjectReader reader = getMetadataObjectReader();
        while (!toLoad.isEmpty())
        {
            String name = toLoad.removeLast();
            if (visited.add(name))
            {
                DistributedBinaryMetadata metadata = loadMetadataFromClassLoader(classLoader, name, reader);
                metadataList.add(metadata);
                toLoad.addAll(metadata.getDependencies());
            }
        }
        return metadataList;
    }

    public static List<DistributedBinaryMetadata> loadAllMetadata(ClassLoader classLoader)
    {
        String directoryName = DistributedMetadataHelper.getMetadataDefinitionsDirectory();
        Enumeration<URL> urls;
        try
        {
            urls = classLoader.getResources(directoryName);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading " + directoryName, e);
        }

        if (!urls.hasMoreElements())
        {
            return Collections.emptyList();
        }

        ObjectReader reader = getMetadataObjectReader();
        Map<String, DistributedBinaryMetadata> index = Maps.mutable.empty();
        while (urls.hasMoreElements())
        {
            loadMetadataFromUrl(urls.nextElement(), reader, m ->
            {
                DistributedBinaryMetadata current = index.put(m.getName(), m);
                if ((current != null) && !current.equals(m))
                {
                    throw new RuntimeException("Conflicting definitions for metadata \"" + m.getName() + "\": " + current + " vs " + m);
                }
            });
        }
        return Lists.mutable.withAll(index.values());
    }

    private static void loadMetadataFromUrl(URL url, ObjectReader objectReader, Consumer<DistributedBinaryMetadata> consumer)
    {
        if ("file".equalsIgnoreCase(url.getProtocol()))
        {
            loadMetadataFromFileUrl(url, objectReader, consumer);
        }
        else if ("jar".equalsIgnoreCase(url.getProtocol()))
        {
            loadMetadataFromJarUrl(url, objectReader, consumer);
        }
    }

    private static void loadMetadataFromFileUrl(URL fileUrl, ObjectReader reader, Consumer<DistributedBinaryMetadata> consumer)
    {
        try
        {
            Path path = Paths.get(fileUrl.toURI());
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path))
            {
                for (Path filePath : dirStream)
                {
                    if (DistributedMetadataHelper.isMetadataDefinitionFileName(filePath.getFileName().toString()))
                    {
                        DistributedBinaryMetadata metadata;
                        try (InputStream stream = Files.newInputStream(filePath))
                        {
                            metadata = reader.readValue(stream);
                        }
                        consumer.accept(metadata);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error loading metadata from " + fileUrl, e);
        }
    }

    private static void loadMetadataFromJarUrl(URL jarUrl, ObjectReader reader, Consumer<DistributedBinaryMetadata> consumer)
    {
        try
        {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = connection.getJarFile())
            {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements())
                {
                    JarEntry entry = entries.nextElement();
                    if (DistributedMetadataHelper.isMetadataDefinitionFilePath(entry.getName()))
                    {
                        DistributedBinaryMetadata metadata;
                        try (InputStream stream = jarFile.getInputStream(entry))
                        {
                            metadata = reader.readValue(stream);
                        }
                        consumer.accept(metadata);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error loading metadata from " + jarUrl, e);
        }
    }

    private static DistributedBinaryMetadata loadMetadataFromClassLoader(ClassLoader classLoader, String metadataName, ObjectReader reader)
    {
        String resourceName = DistributedMetadataHelper.getMetadataDefinitionFilePath(metadataName);
        Enumeration<URL> urls;
        try
        {
            urls = classLoader.getResources(resourceName);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading " + resourceName, e);
        }

        if (!urls.hasMoreElements())
        {
            throw new RuntimeException("Cannot find metadata \"" + metadataName + "\" (resource name \"" + resourceName + "\")");
        }

        // Load metadata
        URL url = urls.nextElement();
        DistributedBinaryMetadata metadata;
        try
        {
            metadata = reader.readValue(url);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading definition of metadata \"" + metadataName + "\" from " + url, e);
        }

        // Check for other possibly conflicting definitions
        while (urls.hasMoreElements())
        {
            URL otherUrl = urls.nextElement();
            DistributedBinaryMetadata otherMetadata;
            try
            {
                otherMetadata = reader.readValue(otherUrl);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error reading definition of metadata \"" + metadataName + "\" from " + otherUrl, e);
            }
            if (!metadata.equals(otherMetadata))
            {
                throw new RuntimeException("Conflicting definitions of metadata \"" + metadataName + "\": " + metadata + " (from " + url + ") vs " + otherMetadata + " (from " + otherUrl + ")");
            }
        }

        return metadata;
    }

    private static ObjectReader getMetadataObjectReader()
    {
        return JsonMapper.builder()
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .build()
                .readerFor(DistributedBinaryMetadata.class);
    }

    private static ObjectWriter getMetadataObjectWriter()
    {
        return JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .enable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
                .build()
                .writerFor(DistributedBinaryMetadata.class);
    }
}
