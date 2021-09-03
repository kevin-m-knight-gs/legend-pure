package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class DistributedBinaryMetadataManager
{
    private final Map<String, DistributedBinaryMetadata> index;

    private DistributedBinaryMetadataManager(Map<String, DistributedBinaryMetadata> index)
    {
        this.index = index;
    }

    public boolean hasMetadata(String name)
    {
        return this.index.containsKey(name);
    }

    public Set<String> getAllMetadataNames()
    {
        return Collections.unmodifiableSet(this.index.keySet());
    }

    public Set<String> computeMetadataClosure(String... metadataNames)
    {
        return computeMetadataClosure(Arrays.asList(metadataNames));
    }

    public Set<String> computeMetadataClosure(Iterable<String> metadataNames)
    {
        Set<String> closure = Sets.mutable.empty();
        Deque<String> searchDeque = Iterate.addAllTo(metadataNames, new ArrayDeque<>());
        while (!searchDeque.isEmpty())
        {
            String nextName = searchDeque.removeLast();
            if (closure.add(nextName))
            {
                DistributedBinaryMetadata metadata = this.index.get(nextName);
                if (metadata == null)
                {
                    throw new IllegalArgumentException("Unknown metadata: \"" + nextName + "\"");
                }
                searchDeque.addAll(metadata.getDependencies());
            }
        }
        return closure;
    }

    public static DistributedBinaryMetadataManager fromClassLoader(ClassLoader classLoader)
    {
        return fromMetadata(getAllMetadataFromClassLoader(classLoader));
    }

    public static DistributedBinaryMetadataManager fromClassLoader(ClassLoader classLoader, String... metadataNames)
    {
        return fromClassLoader(classLoader, Arrays.asList(metadataNames));
    }

    public static DistributedBinaryMetadataManager fromClassLoader(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        return fromMetadata(getMetadataFromClassLoader(classLoader, metadataNames));
    }

    public static DistributedBinaryMetadataManager fromMetadata(DistributedBinaryMetadata... metadata)
    {
        return fromMetadata(Arrays.asList(metadata));
    }

    public static DistributedBinaryMetadataManager fromMetadata(Iterable<? extends DistributedBinaryMetadata> metadata)
    {
        return new DistributedBinaryMetadataManager(indexAndValidateMetadata(metadata));
    }

    private static Map<String, DistributedBinaryMetadata> indexAndValidateMetadata(Iterable<? extends DistributedBinaryMetadata> metadatas)
    {
        Map<String, DistributedBinaryMetadata> index = indexByName(metadatas);
        validateMetadatas(index);
        return index;
    }

    private static Map<String, DistributedBinaryMetadata> indexByName(Iterable<? extends DistributedBinaryMetadata> metadatas)
    {
        Map<String, DistributedBinaryMetadata> index = Maps.mutable.empty();
        metadatas.forEach(metadata ->
        {
            String name = metadata.getName();
            DistributedBinaryMetadata old = index.put(name, metadata);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple metadata named \"" + name + "\"");
            }
        });
        return index;
    }

    private static void validateMetadatas(Map<String, DistributedBinaryMetadata> index)
    {
        index.values().forEach(m -> validateMetadata(m, index));
    }

    private static void validateMetadata(DistributedBinaryMetadata metadata, Map<String, DistributedBinaryMetadata> allMetadatas)
    {
        Collection<String> dependencies = metadata.getDependencies();
        if (!Iterate.allSatisfy(dependencies, allMetadatas::containsKey))
        {
            MutableList<String> missing = Iterate.reject(dependencies, allMetadatas::containsKey, Lists.mutable.empty());
            StringBuilder builder = new StringBuilder("Metadata \"").append(metadata.getName()).append("\" is missing ");
            if (missing.size() == 1)
            {
                builder.append("dependency \"").append(missing.get(0)).append('"');
            }
            else
            {
                missing.sortThis().appendString(builder, "dependencies: \"", "\", \"", "\"");
            }
            throw new IllegalArgumentException(builder.toString());
        }
    }

    private static List<DistributedBinaryMetadata> getMetadataFromClassLoader(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        Set<String> visited = Sets.mutable.empty();
        List<DistributedBinaryMetadata> metadataList = Lists.mutable.empty();
        Deque<String> toLoad = Iterate.addAllTo(metadataNames, new ArrayDeque<>());
        ObjectReader reader = getMetadataObjectReader();
        while (!toLoad.isEmpty())
        {
            String name = toLoad.removeLast();
            if (!visited.add(name))
            {
                DistributedBinaryMetadata metadata = loadMetadataFromClassLoader(classLoader, name, reader);
                metadataList.add(metadata);
                toLoad.addAll(metadata.getDependencies());
            }
        }
        return metadataList;
    }

    private static List<DistributedBinaryMetadata> getAllMetadataFromClassLoader(ClassLoader classLoader)
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

        MutableSet<String> metadataNames = Sets.mutable.empty();
        while (urls.hasMoreElements())
        {
            forEachDirectoryEntry(urls.nextElement(), p -> getMetadataNameFromFileName(p).ifPresent(metadataNames::add));
        }
        ObjectReader reader = getMetadataObjectReader();
        return metadataNames.collect(n -> loadMetadataFromClassLoader(classLoader, n, reader), Lists.mutable.ofInitialCapacity(metadataNames.size()));
    }

    private static void forEachDirectoryEntry(URL directoryUrl, Consumer<Path> consumer)
    {
        try
        {
            forEachDirectoryEntry(directoryUrl.toURI(), consumer);
        }
        catch (IOException | URISyntaxException e)
        {
            throw new RuntimeException("Error accessing " + directoryUrl, e);
        }
    }

    private static void forEachDirectoryEntry(URI directoryUri, Consumer<Path> consumer) throws IOException
    {
        try
        {
            // Try to get the filesystem for the URI
            FileSystem fileSystem = FileSystems.getFileSystem(directoryUri);
            forEachDirectoryEntry(fileSystem, directoryUri, consumer);
        }
        catch (FileSystemNotFoundException ignore)
        {
            // If the filesystem does not already exist, try to create it (making sure to close it when done)
            try (FileSystem fileSystem = FileSystems.newFileSystem(directoryUri, Collections.emptyMap()))
            {
                forEachDirectoryEntry(fileSystem, directoryUri, consumer);
            }
            catch (FileSystemAlreadyExistsException ignoreAlso)
            {
                // If the filesystem was created in the meantime, try to get it again
                FileSystem fileSystem = FileSystems.getFileSystem(directoryUri);
                forEachDirectoryEntry(fileSystem, directoryUri, consumer);
            }
        }
    }

    private static void forEachDirectoryEntry(FileSystem fileSystem, URI directoryUri, Consumer<Path> consumer) throws IOException
    {
        Path path = fileSystem.provider().getPath(directoryUri);
        forEachDirectoryEntry(path, consumer);
    }

    private static void forEachDirectoryEntry(Path directory, Consumer<Path> consumer) throws IOException
    {
        if (Files.isDirectory(directory))
        {
            Files.list(directory).forEach(consumer);
        }
    }

    private static Optional<String> getMetadataNameFromFileName(Path fileName)
    {
        return getMetadataNameFromFileName(fileName.getFileName().toString());
    }

    private static Optional<String> getMetadataNameFromFileName(String fileName)
    {
        String extension = DistributedMetadataHelper.getMetadataDefinitionFileExtension();
        if (fileName.endsWith(extension))
        {
            String metadataName = fileName.substring(0, fileName.length() - extension.length());
            if (DistributedMetadataHelper.isValidMetadataName(metadataName))
            {
                return Optional.of(metadataName);
            }
        }
        return Optional.empty();
    }

    private static ObjectReader getMetadataObjectReader()
    {
        return JsonMapper.builder().build().readerFor(DistributedBinaryMetadata.class);
    }

    private static DistributedBinaryMetadata loadMetadataFromClassLoader(ClassLoader classLoader, String metadataName, ObjectReader reader)
    {
        String resourceName = DistributedMetadataHelper.getMetadataDefinitionFilePath(metadataName);
        URL url = classLoader.getResource(resourceName);
        if (url == null)
        {
            throw new IllegalArgumentException("Cannot find metadata \"" + metadataName + "\" (resource name \"" + resourceName + "\")");
        }
        return loadMetadataFromURL(url, metadataName, reader);
    }

    private static DistributedBinaryMetadata loadMetadataFromURL(URL url, String metadataName, ObjectReader reader)
    {
        try
        {
            return reader.readValue(url);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading definition of metadata \"" + metadataName + "\" from " + url, e);
        }
    }
}
