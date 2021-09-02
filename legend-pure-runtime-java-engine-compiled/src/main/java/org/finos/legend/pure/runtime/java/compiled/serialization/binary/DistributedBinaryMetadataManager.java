package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.runtime.java.compiled.metadata.MetadataLazy;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.ServiceLoader;
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
                    throw new IllegalStateException("Unknown metadata: \"" + nextName + "\"");
                }
                searchDeque.addAll(metadata.getDependencies());
            }
        }
        return closure;
    }

    public MultiDistributedBinaryGraphDeserializer getDeserializer(FileReader fileReader)
    {
        return MultiDistributedBinaryGraphDeserializer.fromFileReader(getAllMetadataNames(), fileReader);
    }

    public MultiDistributedBinaryGraphDeserializer getDeserializer(FileReader fileReader, Iterable<String> metadataNames)
    {
        return MultiDistributedBinaryGraphDeserializer.fromFileReader(metadataNames, fileReader);
    }

    public MetadataLazy getMetadataLazy(ClassLoader classLoader)
    {
        return MetadataLazy.fromClassLoader(classLoader, getAllMetadataNames());
    }

    public MetadataLazy getMetadataLazy(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        return MetadataLazy.fromClassLoader(classLoader, computeMetadataClosure(metadataNames));
    }

    public static DistributedBinaryMetadataManager fromAvailableMetadata()
    {
        return fromMetadatas(ServiceLoader.load(DistributedBinaryMetadata.class));
    }

    public static DistributedBinaryMetadataManager fromAvailableMetadata(ClassLoader classLoader)
    {
        return fromMetadatas(ServiceLoader.load(DistributedBinaryMetadata.class, classLoader));
    }

    private static DistributedBinaryMetadataManager fromMetadatas(Iterable<? extends DistributedBinaryMetadata> metadatas)
    {
        return new DistributedBinaryMetadataManager(indexAndValidateMetadata(metadatas));
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
                builder.append("dependency: \"").append(missing.get(0)).append('"');
            }
            else
            {
                missing.sortThis().appendString(builder, "dependencies: \"", "\", \"", "\"");
            }
            throw new RuntimeException(builder.toString());
        }
    }
}
