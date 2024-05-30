// Copyright 2024 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;

public abstract class AbstractMetadataTest
{
    protected static ConcreteElementMetadata newClass(String path, String sourceId, int startLine, int startCol, int endLine, int endCol, int referenceIdVersion, String... externalReferences)
    {
        return newElement(path, M3Paths.Class, sourceId, startLine, startCol, endLine, endCol, referenceIdVersion, externalReferences);
    }

    protected static ConcreteElementMetadata newAssociation(String path, String sourceId, int startLine, int startCol, int endLine, int endCol, int referenceIdVersion, String... externalReferences)
    {
        return newElement(path, M3Paths.Association, sourceId, startLine, startCol, endLine, endCol, referenceIdVersion, externalReferences);
    }

    protected static ConcreteElementMetadata newEnumeration(String path, String sourceId, int startLine, int startCol, int endLine, int endCol, int referenceIdVersion, String... externalReferences)
    {
        return newElement(path, M3Paths.Enumeration, sourceId, startLine, startCol, endLine, endCol, referenceIdVersion, externalReferences);
    }

    protected static ConcreteElementMetadata newElement(String path, String classifierPath, String sourceId, int startLine, int startCol, int endLine, int endCol, int referenceIdVersion, String... externalReferences)
    {
        return newElement(path, classifierPath, newSourceInfo(sourceId, startLine, startCol, endLine, endCol), referenceIdVersion, externalReferences);
    }

    protected static ConcreteElementMetadata newElement(String path, String classifierPath, SourceInformation sourceInfo, int referenceIdVersion, String... externalReferences)
    {
        return ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(classifierPath)
                .withSourceInformation(sourceInfo)
                .withReferenceIdVersion(referenceIdVersion)
                .withExternalReferences(Arrays.asList(externalReferences))
                .build();
    }

    protected static SourceInformation newSourceInfo(String sourceId, int startLine, int startCol, int endLine, int endCol)
    {
        return new SourceInformation(sourceId, startLine, startCol, startLine, startCol, endLine, endCol);
    }
}
