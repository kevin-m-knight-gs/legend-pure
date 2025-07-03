// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.runtime.java.compiled.generation.processors;

import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m3.serialization.compiler.reference.v1.ReferenceIdExtensionV1;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.RepositoryCodeStorage;
import org.finos.legend.pure.m3.serialization.runtime.Source;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Objects;

public class IdBuilder
{
    private final ProcessorSupport processorSupport;
    private final String defaultIdPrefix;
    private final boolean allowNonReferenceIds;
    private volatile ReferenceIdProvider idProvider;
    private final boolean hashIds;

    private IdBuilder(ProcessorSupport processorSupport, String defaultIdPrefix, boolean allowNonReferenceIds, boolean hashIds)
    {
        this.processorSupport = Objects.requireNonNull(processorSupport, "processorSupport may not be null");
        this.defaultIdPrefix = defaultIdPrefix;
        this.allowNonReferenceIds = allowNonReferenceIds;
        this.hashIds = hashIds;
    }

    public String buildId(CoreInstance instance)
    {
        String id = buildIdInternal(instance);
        return this.hashIds ? hashToBase64String(id) : id;
    }

    private String buildIdInternal(CoreInstance instance)
    {
        ReferenceIdProvider provider = getIdProvider();
        if (!this.allowNonReferenceIds || provider.hasReferenceId(instance))
        {
            return provider.getReferenceId(instance);
        }

        int syntheticId = instance.getSyntheticId();
        return (this.defaultIdPrefix == null) ? Integer.toString(syntheticId) : (this.defaultIdPrefix + syntheticId);
    }

    private ReferenceIdProvider getIdProvider()
    {
        ReferenceIdProvider local = this.idProvider;
        if (local == null)
        {
            synchronized (this)
            {
                if ((local = this.idProvider) == null)
                {
                    return this.idProvider = new ReferenceIdExtensionV1().newProvider(this.processorSupport);
                }
            }
        }
        return local;
    }

    public static class Builder
    {
        private ProcessorSupport processorSupport;
        private String defaultIdPrefix;
        private boolean allowNonReferenceIds = true;
        private boolean hashIds = false;

        private Builder()
        {
        }

        public void setProcessorSupport(ProcessorSupport processorSupport)
        {
            this.processorSupport = processorSupport;
        }

        public Builder withProcessorSupport(ProcessorSupport processorSupport)
        {
            setProcessorSupport(processorSupport);
            return this;
        }

        public void setDefaultIdPrefix(String prefix)
        {
            this.defaultIdPrefix = prefix;
            if (this.defaultIdPrefix != null)
            {
                allowNonReferenceIds();
            }
        }

        public Builder withDefaultIdPrefix(String prefix)
        {
            setDefaultIdPrefix(prefix);
            return this;
        }

        public void setAllowNonReferenceIds(boolean allowNonReferenceIds)
        {
            this.allowNonReferenceIds = allowNonReferenceIds;
        }

        public void allowNonReferenceIds()
        {
            setAllowNonReferenceIds(true);
        }

        public void disallowNonReferenceIds()
        {
            setAllowNonReferenceIds(false);
        }

        public Builder withNonReferenceIdsAllowed(boolean allowNonReferenceIds)
        {
            setAllowNonReferenceIds(allowNonReferenceIds);
            return this;
        }

        public Builder withNonReferenceIdsAllowed()
        {
            return withNonReferenceIdsAllowed(true);
        }

        public Builder withNonReferenceIdsDisallowed()
        {
            return withNonReferenceIdsAllowed(false);
        }

        public void setHashIds(boolean hashIds)
        {
            this.hashIds = hashIds;
        }

        public void hashIds()
        {
            setHashIds(true);
        }

        public Builder withHashIds(boolean hashIds)
        {
            setHashIds(hashIds);
            return this;
        }

        public Builder withIdsHashed()
        {
            return withHashIds(true);
        }

        public IdBuilder build()
        {
            return newIdBuilder(this.processorSupport, this.defaultIdPrefix, this.allowNonReferenceIds, this.hashIds);
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(ProcessorSupport processorSupport)
    {
        return builder().withProcessorSupport(processorSupport);
    }

    public static IdBuilder newIdBuilder(String defaultIdPrefix, ProcessorSupport processorSupport)
    {
        return newIdBuilder(processorSupport, defaultIdPrefix, true, false);
    }

    public static IdBuilder newIdBuilder(ProcessorSupport processorSupport)
    {
        return newIdBuilder(processorSupport, true);
    }

    public static IdBuilder newIdBuilder(ProcessorSupport processorSupport, boolean allowNonReferenceIds)
    {
        return newIdBuilder(processorSupport, null, allowNonReferenceIds, false);
    }

    private static IdBuilder newIdBuilder(ProcessorSupport processorSupport, String defaultIdPrefix, boolean allowNonReferenceIds, boolean hashIds)
    {
        return new IdBuilder(processorSupport, defaultIdPrefix, allowNonReferenceIds, hashIds);
    }

    public static String sourceToId(SourceInformation sourceInformation)
    {
        String sourceId = sourceInformation.getSourceId();
        if (Source.isInMemory(sourceId))
        {
            return CodeStorageTools.hasPureFileExtension(sourceId) ? sourceId.substring(0, sourceId.length() - RepositoryCodeStorage.PURE_FILE_EXTENSION.length()) : sourceId;
        }

        int endIndex = CodeStorageTools.hasPureFileExtension(sourceId) ? (sourceId.length() - RepositoryCodeStorage.PURE_FILE_EXTENSION.length()) : sourceId.length();
        return sourceId.substring(1, endIndex).replace('/', '_');
    }

    public static String hashToBase64String(String instanceId)
    {
        long hashedInstanceId = hash(instanceId);
        byte[] longBytes = ByteBuffer.allocate(8).putLong(hashedInstanceId).array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(longBytes);
    }

    private static long hash(String id)
    {
        long value = 0;
        for (int i = 0, codePoint; i < id.length(); i += Character.charCount(codePoint))
        {
            value = (31 * value) + (codePoint = id.codePointAt(i));
        }
        return value;
    }
}
