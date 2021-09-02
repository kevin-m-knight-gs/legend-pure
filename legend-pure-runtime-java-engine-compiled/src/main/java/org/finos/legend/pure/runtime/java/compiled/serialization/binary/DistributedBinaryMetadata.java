package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Sets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class DistributedBinaryMetadata
{
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .build();

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
}
