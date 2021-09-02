package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static DistributedBinaryMetadataManager fromClassLoader(ClassLoader classLoader, String... metadataNames)
    {
        return fromClassLoader(classLoader, Arrays.asList(metadataNames));
    }

    public static DistributedBinaryMetadataManager fromClassLoader(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        Set<String> visited = Sets.mutable.empty();
        List<DistributedBinaryMetadata> metadatas = Lists.mutable.empty();
        Deque<String> toLoad = Iterate.addAllTo(metadataNames, new ArrayDeque<>());
        JsonMapper jsonMapper = JsonMapper.builder().build();
        while (!toLoad.isEmpty())
        {
            String name = toLoad.removeLast();
            if (!visited.add(name))
            {
                URL url = classLoader.getResource(DistributedMetadataHelper.getMetadataDefinitionFilePath(name));
                if (url == null)
                {
                    throw new IllegalArgumentException("Cannot find metadata \"" + name + "\"");
                }
                DistributedBinaryMetadata metadata;
                try
                {
                    metadata = jsonMapper.readValue(url, DistributedBinaryMetadata.class);
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Error reading definition of metadata \"" + name + "\"");
                }
                metadatas.add(metadata);
                toLoad.addAll(metadata.getDependencies());
            }
        }
        return fromMetadata(metadatas);
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
}
