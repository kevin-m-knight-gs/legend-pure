// Copyright 2025 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.junit.Assert;
import org.junit.Test;

public class TestExternalReference
{
    @Test
    public void testReferenceIdIsRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, ExternalReference.builder()::build);
        Assert.assertEquals("reference id is required", e.getMessage());
    }

    @Test
    public void testReferenceIdIsNonEmpty()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, ExternalReference.builder().withReferenceId("")::build);
        Assert.assertEquals("reference id may not be empty", e.getMessage());
    }

    @Test
    public void testMerge()
    {
        ExternalReference baseRef = ExternalReference.builder().withReferenceId("test::model::LeftClass").build();
        Assert.assertEquals(baseRef, baseRef.merge());
        Assert.assertEquals(baseRef, baseRef.merge(Lists.immutable.empty()));
        Assert.assertEquals(baseRef, baseRef.merge(baseRef));

        ExternalReference refWithPropFromAssoc = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withPropertyFromAssociation("test::model::LeftRight.properties['toRight']")
                .build();
        Assert.assertEquals(refWithPropFromAssoc, refWithPropFromAssoc.merge(refWithPropFromAssoc));
        Assert.assertEquals(refWithPropFromAssoc, baseRef.merge(refWithPropFromAssoc));
        Assert.assertEquals(refWithPropFromAssoc, baseRef.merge(refWithPropFromAssoc, refWithPropFromAssoc));

        ExternalReference refWithQPropFromAssoc = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withQualifiedPropertyFromAssociation("test::model::LeftRight.qualifiedProperties[id='toRight(Integer[1])']")
                .build();
        Assert.assertEquals(refWithQPropFromAssoc, baseRef.merge(refWithQPropFromAssoc));

        ExternalReference refWithRefUsages1 = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withReferenceUsage("test::model::LeftRight.properties['toLeft'].classifierGenericType.typeArguments[1]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.properties['toRight'].classifierGenericType.typeArguments[0]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l'].genericType", "rawType", 0)
                .build();
        Assert.assertEquals(refWithRefUsages1, refWithRefUsages1.merge(refWithRefUsages1));
        Assert.assertEquals(refWithRefUsages1, baseRef.merge(refWithRefUsages1));

        ExternalReference refWithRefUsages2 = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withReferenceUsage("test::model::LeftRight.properties['toLeft'].classifierGenericType.typeArguments[1]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.properties['toRight'].classifierGenericType.typeArguments[0]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType", 0)
                .build();
        Assert.assertEquals(refWithRefUsages2, refWithRefUsages2.merge(refWithRefUsages2));
        Assert.assertEquals(refWithRefUsages2, baseRef.merge(refWithRefUsages2));

        ExternalReference refWithRefUsagesCombined = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withReferenceUsage("test::model::LeftRight.properties['toLeft'].classifierGenericType.typeArguments[1]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.properties['toRight'].classifierGenericType.typeArguments[0]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l'].genericType", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType", 0)
                .build();
        Assert.assertEquals(refWithRefUsagesCombined, baseRef.merge(refWithRefUsages1, refWithRefUsages2));
        Assert.assertEquals(refWithRefUsagesCombined, baseRef.merge(refWithRefUsages2, refWithRefUsages1));
        Assert.assertEquals(refWithRefUsagesCombined, refWithRefUsages1.merge(refWithRefUsages2));
        Assert.assertEquals(refWithRefUsagesCombined, refWithRefUsages2.merge(refWithRefUsages1));

        ExternalReference fullRef = ExternalReference.builder()
                .withReferenceId("test::model::LeftClass")
                .withPropertyFromAssociation("test::model::LeftRight.properties['toRight']")
                .withQualifiedPropertyFromAssociation("test::model::LeftRight.qualifiedProperties[id='toRight(Integer[1])']")
                .withReferenceUsage("test::model::LeftRight.properties['toLeft'].classifierGenericType.typeArguments[1]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.properties['toRight'].classifierGenericType.typeArguments[0]", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l'].genericType", "rawType", 0)
                .withReferenceUsage("test::model::LeftRight.qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType", 0)
                .build();
        Assert.assertEquals(fullRef, baseRef.merge(refWithPropFromAssoc, refWithQPropFromAssoc, refWithRefUsages1, refWithRefUsages2));
        Assert.assertEquals(fullRef, baseRef.merge(refWithRefUsages1, refWithRefUsages2, refWithPropFromAssoc, refWithQPropFromAssoc));
        Assert.assertEquals(fullRef, baseRef.merge(refWithRefUsages2, refWithRefUsages1, refWithQPropFromAssoc, refWithPropFromAssoc));
    }

    @Test
    public void testInvalidMerge()
    {
        ExternalReference ref1 = ExternalReference.builder().withReferenceId(M3Paths.Class).build();
        ExternalReference ref2 = ExternalReference.builder().withReferenceId(M3Paths.Association).build();

        IllegalArgumentException e1 = Assert.assertThrows(IllegalArgumentException.class, () -> ref1.merge(ref2));
        Assert.assertEquals("Cannot merge external reference for '" + M3Paths.Association + "' into '" + M3Paths.Class + "'", e1.getMessage());

        IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> ref2.merge(ref1));
        Assert.assertEquals("Cannot merge external reference for '" + M3Paths.Class + "' into '" + M3Paths.Association + "'", e2.getMessage());
    }
}
