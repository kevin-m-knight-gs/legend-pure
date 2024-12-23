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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Profile;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.NativeFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Measure;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m3.serialization.compiler.reference.v1.ReferenceIdExtensionV1;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestConcreteElementMetadataGenerator extends AbstractReferenceTest
{
    private static ReferenceIds referenceIds;
    private static ConcreteElementMetadataGenerator generator;

    @BeforeClass
    public static void setUpGenerator()
    {
        referenceIds = ReferenceIds.builder(processorSupport).withExtension(new ReferenceIdExtensionV1()).build();
        generator = new ConcreteElementMetadataGenerator(referenceIds.provider(), processorSupport);
    }

    @Test
    public void testSimpleClass()
    {
        String path = "test::model::SimpleClass";
        Class<?> simpleClass = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(simpleClass.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .build();
        assertMetadata(expected, simpleClass);
    }

    @Test
    public void testEnumeration()
    {
        String path = "test::model::SimpleEnumeration";
        Enumeration<? extends Enum> simpleEnumeration = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Enumeration)
                .withSourceInformation(simpleEnumeration.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .build();
        assertMetadata(expected, simpleEnumeration);
    }

    @Test
    public void testAssociation()
    {
        String path = "test::model::LeftRight";
        String leftPath = "test::model::Left";
        String rightPath = "test::model::Right";

        Association leftRight = getCoreInstance(path);
        ConcreteElementMetadata expectedLR = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Association)
                .withSourceInformation(leftRight.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReferences("meta::pure::functions::boolean::equal_Any_MANY__Any_MANY__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func")
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences(leftPath,
                        path + ".properties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[0].genericType.rawType.resolvedNode",
//                        path + ".properties[1].classifierGenericType.typeArguments[0].rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode")
                .withExternalReferences(rightPath,
                        path + ".properties[0].classifierGenericType.typeArguments[0].rawType.resolvedNode",
//                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode")
                .withExternalReference(leftPath + ".properties['name']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReference(rightPath + ".properties['id']",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .build();
        assertMetadata(expectedLR, leftRight);

        Class<?> left = getCoreInstance(leftPath);
        ConcreteElementMetadata expectedLeft = ConcreteElementMetadata.builder()
                .withPath(leftPath)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(left.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", leftPath + ".package")
                .build();
        assertMetadata(expectedLeft, left);

        Class<?> right = getCoreInstance(rightPath);
        ConcreteElementMetadata expectedRight = ConcreteElementMetadata.builder()
                .withPath(rightPath)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(right.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", rightPath + ".package")
                .build();
        assertMetadata(expectedRight, right);
    }

    @Test
    public void testSimpleProfile()
    {
        String path = "test::model::SimpleProfile";
        Profile simpleProfile = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Profile)
                .withSourceInformation(simpleProfile.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .build();
        assertMetadata(expected, simpleProfile);
    }

    @Test
    public void testClassWithGeneralizations()
    {
        String path = "test::model::BothSides";
        Class<?> bothSides = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(bothSides.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReference("test::model::Left", path + ".generalizations[0].general.rawType.resolvedNode")
                .withExternalReference("test::model::Right", path + ".generalizations[1].general.rawType.resolvedNode")
                .build();
        assertMetadata(expected, bothSides);
    }

    @Test
    public void testClassWithAnnotations()
    {
        String path = "test::model::ClassWithAnnotations";
        Class<?> classWithAnnotations = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithAnnotations.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("meta::pure::profiles::doc.p_stereotypes[value='deprecated']",
                        path + ".stereotypes.resolvedNode",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode")
                .withExternalReferences("meta::pure::profiles::doc.p_tags[value='doc']",
                        path + ".taggedValues.tag.resolvedNode",
                        path + ".properties[1].taggedValues.tag.resolvedNode",
                        path + ".properties[2].taggedValues[0].tag.resolvedNode")
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='todo']",
                        path + ".properties[2].taggedValues[1].tag.resolvedNode")
                .build();
        assertMetadata(expected, classWithAnnotations);
    }

    @Test
    public void testClassWithTypeAndMultiplicityParameters()
    {
        String path = "test::model::ClassWithTypeAndMultParams";
        Class<?> classWithTypeMultParams = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithTypeMultParams.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .build();
        assertMetadata(expected, classWithTypeMultParams);
    }

    @Test
    public void testClassWithQualifiedProperties()
    {
        String path = "test::model::ClassWithQualifiedProperties";
        Class<?> classWithQualifiedProps = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithQualifiedProps.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].parametersValues[0].func")
                .withExternalReference("meta::pure::functions::boolean::not_Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].func")
                .withExternalReference("meta::pure::functions::collection::at_T_MANY__Integer_1__T_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[2].values.expressionSequence.func")
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_MANY__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].func")
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues.func")
                .withExternalReferences("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].func")
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        path + ".qualifiedProperties[2].expressionSequence[0].func")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].parametersValues[1].values.expressionSequence.parametersValues.values[0].func")
                .withExternalReference("meta::pure::functions::string::joinStrings_String_MANY__String_1__String_1__String_1__String_1_",
                        path + ".qualifiedProperties[2].expressionSequence[1].func")
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_",
                        path + ".qualifiedProperties[2].expressionSequence[0].parametersValues[1].parametersValues[1].values.expressionSequence.func")
                .build();
        assertMetadata(expected, classWithQualifiedProps);
    }

    @Test
    public void testClassWithMilestoning1()
    {
        String path = "test::model::ClassWithMilestoning1";
        Class<?> classWithMilestoning1 = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithMilestoning1.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning2",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[2].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[2].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning2.properties['processingDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3",
                        path + ".originalMilestonedProperties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[3].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['businessDate']",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['processingDate']",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func")
                .withExternalReferences("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func")
                .withExternalReferences("meta::pure::milestoning::BusinessDateMilestoning",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[2].stereotypes.resolvedNode",
                        path + ".properties[3].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='businesstemporal']",
                        path + ".stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, classWithMilestoning1);
    }

    @Test
    public void testClassWithMilestoning2()
    {
        String path = "test::model::ClassWithMilestoning2";
        Class<?> classWithMilestoning2 = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithMilestoning2.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning1",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[2].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[2].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning1.properties['businessDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3",
                        path + ".originalMilestonedProperties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[3].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['businessDate']",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['processingDate']",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func")
                .withExternalReferences("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func")
                .withExternalReferences("meta::pure::milestoning::ProcessingDateMilestoning",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[2].stereotypes.resolvedNode",
                        path + ".properties[3].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='processingtemporal']",
                        path + ".stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, classWithMilestoning2);
    }

    @Test
    public void testClassWithMilestoning3()
    {
        String path = "test::model::ClassWithMilestoning3";
        Class<?> classWithMilestoning3 = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(classWithMilestoning3.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning1",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[3].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning1.properties['businessDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning2",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[4].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[4].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning2.properties['processingDate']",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues.func",
                        path + ".qualifiedProperties[5].expressionSequence.parametersValues.func")
                .withExternalReferences("meta::pure::functions::collection::first_T_MANY__T_$0_1$_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.func",
                        path + ".qualifiedProperties[5].expressionSequence.func")
                .withExternalReferences("meta::pure::milestoning::BiTemporalMilestoning",
                        path + ".properties[2].genericType.rawType.resolvedNode",
                        path + ".properties[2].classifierGenericType.typeArguments[1].rawType.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[3].stereotypes.resolvedNode",
                        path + ".properties[4].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[4].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[5].stereotypes.resolvedNode")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode",
                        path + ".properties[2].stereotypes.resolvedNode")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='bitemporal']",
                        path + ".stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, classWithMilestoning3);
    }

    @Test
    public void testAssociationWithMilestoning1()
    {
        String path = "test::model::AssociationWithMilestoning1";
        Association association = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Association)
                .withSourceInformation(association.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning1",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning1.properties['businessDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning2",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[0].rawType.resolvedNode",
                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning2.properties['processingDate']",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, association);
    }

    @Test
    public void testAssociationWithMilestoning2()
    {
        String path = "test::model::AssociationWithMilestoning2";
        Association association = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Association)
                .withSourceInformation(association.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning1",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[1].parametersValues.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning1.properties['businessDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[1].func")
                .withExternalReferences("test::model::ClassWithMilestoning3",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[0].rawType.resolvedNode",
                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['businessDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['processingDate']",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.func")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[4].stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, association);
    }

    @Test
    public void testAssociationWithMilestoning3()
    {
        String path = "test::model::AssociationWithMilestoning3";
        Association association = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Association)
                .withSourceInformation(association.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("test::model::ClassWithMilestoning2",
                        path + ".originalMilestonedProperties[0].genericType.rawType.resolvedNode",
                        path + ".properties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".properties[0].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[1].parametersValues.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning2.properties['processingDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[1].func")
                .withExternalReferences("test::model::ClassWithMilestoning3",
                        path + ".originalMilestonedProperties[1].genericType.rawType.resolvedNode",
                        path + ".properties[1].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].genericType.rawType.resolvedNode",
                        path + ".originalMilestonedProperties[0].classifierGenericType.typeArguments[0].rawType.resolvedNode",
                        path + ".properties[1].classifierGenericType.typeArguments[1].rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].classifierGenericType.typeArguments.rawType.returnType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[1].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[2].classifierGenericType.typeArguments.rawType.parameters[0].genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].genericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.classifierGenericType.typeArguments.rawType.parameters.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].parametersValues.genericType.rawType.resolvedNode",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].parametersValues.genericType.rawType.resolvedNode")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['processingDate']",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].parametersValues[0].func")
                .withExternalReferences("test::model::ClassWithMilestoning3.properties['businessDate']",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].parametersValues[0].func")
                .withExternalReferences("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.func")
                .withExternalReferences("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        path + ".qualifiedProperties[0].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.parametersValues[1].values.expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[3].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[0].func",
                        path + ".qualifiedProperties[4].expressionSequence.parametersValues[1].values.expressionSequence.parametersValues[1].func")
                .withExternalReferences("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        path + ".qualifiedProperties[0].expressionSequence.func",
                        path + ".qualifiedProperties[1].expressionSequence.func",
                        path + ".qualifiedProperties[2].expressionSequence.func",
                        path + ".qualifiedProperties[3].expressionSequence.func",
                        path + ".qualifiedProperties[4].expressionSequence.func")
                .withExternalReferences("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        path + ".properties[0].stereotypes.resolvedNode",
                        path + ".properties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[0].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[1].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[2].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[3].stereotypes.resolvedNode",
                        path + ".qualifiedProperties[4].stereotypes.resolvedNode")
                .build();
        assertMetadata(expected, association);
    }

    @Test
    public void testNativeFunction()
    {
        String path = "meta::pure::functions::lang::compare_T_1__T_1__Integer_1_";
        NativeFunction<?> compare = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.NativeFunction)
                .withSourceInformation(compare.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='doc']",
                        path + ".taggedValues[0].tag.resolvedNode")
                .withExternalReference("meta::pure::test::pct::PCT.p_stereotypes[value='function']",
                        path + ".stereotypes.resolvedNode")
                .withExternalReference("meta::pure::test::pct::PCT.p_tags[value='grammarDoc']",
                        path + ".taggedValues[1].tag.resolvedNode")
                .build();
        assertMetadata(expected, compare);
    }

    @Test
    public void testFunction()
    {
        String path = "test::model::testFunc_T_m__Function_$0_1$__String_m_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.ConcreteFunctionDefinition)
                .withSourceInformation(testFunction.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_",
                        path + ".expressionSequence[0].parametersValues[1].parametersValues[0].func")
                .withExternalReference("meta::pure::functions::collection::map_T_m__Function_1__V_m_",
                        path + ".expressionSequence[1].func")
                .withExternalReference("meta::pure::functions::lang::eval_Function_1__T_n__V_m_",
                        path + ".expressionSequence[1].parametersValues[1].values.expressionSequence.func")
                .withExternalReference("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_",
                        path + ".expressionSequence[0].parametersValues[1].func")
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        path + ".expressionSequence[0].func")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values.expressionSequence.func")
                .withExternalReference("meta::pure::functions::string::toString_Any_1__String_1_",
                        path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values.expressionSequence.values.expressionSequence.func")
                .build();
        assertMetadata(expected, testFunction);
    }

    @Test
    public void testFunction2()
    {
        String path = "test::model::testFunc2__String_1_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.ConcreteFunctionDefinition)
                .withSourceInformation(testFunction.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReferences("test::model",
                        path + ".expressionSequence[0].parametersValues[1].values.resolvedNode",
                        path + ".package")
                .withExternalReference("test::model::Mass.nonCanonicalUnits['Pound']",
                        path + ".expressionSequence[1].parametersValues[1].values.resolvedNode")
                .withExternalReferences("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        path + ".expressionSequence[0].func",
                        path + ".expressionSequence[1].func")
                .withExternalReference("meta::pure::functions::meta::elementToPath_PackageableElement_1__String_1_",
                        path + ".expressionSequence[2].parametersValues.values[0].func")
                .withExternalReferences("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        path + ".expressionSequence[2].parametersValues.values[2].func",
                        path + ".expressionSequence[2].parametersValues.values[4].func")
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_",
                        path + ".expressionSequence[2].func")
                .build();
        assertMetadata(expected, testFunction);
    }

    @Test
    public void testFunction3()
    {
        String path = "test::model::testFunc3__Any_MANY_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.ConcreteFunctionDefinition)
                .withSourceInformation(testFunction.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReferences("test::model",
                        path + ".expressionSequence.values[0].values.resolvedNode",
                        path + ".expressionSequence.values[4].expressionSequence.parametersValues.values.resolvedNode",
                        path + ".package")
                .build();
        assertMetadata(expected, testFunction);
    }

    @Test
    public void testMeasureWithNonconvertibleUnits()
    {
        String path = "test::model::Currency";
        Measure currency = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Measure)
                .withSourceInformation(currency.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .build();
        assertMetadata(expected, currency);
    }

    @Test
    public void testMeasureWithConveritbleUnits()
    {
        String path = "test::model::Mass";
        Measure mass = getCoreInstance(path);
        ConcreteElementMetadata expected = ConcreteElementMetadata.builder()
                .withPath(path)
                .withClassifierPath(M3Paths.Measure)
                .withSourceInformation(mass.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference("test::model", path + ".package")
                .withExternalReferences("meta::pure::functions::math::times_Number_MANY__Number_1_",
                        path + ".nonCanonicalUnits[0].conversionFunction.expressionSequence.func",
                        path + ".nonCanonicalUnits[1].conversionFunction.expressionSequence.func")
                .build();
        assertMetadata(expected, mass);
    }

    private void assertMetadata(ConcreteElementMetadata expected, CoreInstance concreteElement)
    {
        ConcreteElementMetadata actual = generator.generateMetadata(concreteElement);
        if ((expected != null) && (actual != null) && !expected.equals(actual))
        {
            Assert.assertEquals("path mismatch", expected.getPath(), actual.getPath());
            Assert.assertEquals("classifier mismatch", expected.getClassifierPath(), actual.getClassifierPath());
            Assert.assertEquals("source info mismatch", expected.getSourceInformation(), actual.getSourceInformation());

            if (!expected.getExternalReferences().equals(actual.getExternalReferences()))
            {
                // the difference is in external references
                MutableMap<String, MutableSet<String>> expectedSets = Maps.mutable.empty();
                expected.getExternalReferences().forEachKeyValue((refId, refPaths) -> expectedSets.put(refId, refPaths.collect(GraphPath::getDescription, Sets.mutable.empty())));
                MutableMap<String, MutableSet<String>> actualSets = Maps.mutable.empty();
                actual.getExternalReferences().forEachKeyValue((refId, refPaths) -> actualSets.put(refId, refPaths.collect(GraphPath::getDescription, Sets.mutable.empty())));

                MutableList<Pair<String, MutableList<String>>> expectedMismatches = Lists.mutable.empty();
                MutableList<Pair<String, MutableList<String>>> actualMismatches = Lists.mutable.empty();
                expectedSets.forEachKeyValue((refId, expectedPaths) ->
                {
                    MutableSet<String> actualPaths = actualSets.get(refId);
                    if (actualPaths == null)
                    {
                        expectedMismatches.add(Tuples.pair(refId, expectedPaths.toSortedList()));
                    }
                    else if (!expectedPaths.equals(actualPaths))
                    {
                        MutableList<String> expectedNotActual = expectedPaths.reject(actualPaths::contains, Lists.mutable.empty());
                        if (expectedNotActual.notEmpty())
                        {
                            expectedMismatches.add(Tuples.pair(refId, expectedNotActual.sortThis()));
                        }
                        MutableList<String> actualNotExpected = actualPaths.reject(expectedPaths::contains, Lists.mutable.empty());
                        if (actualNotExpected.notEmpty())
                        {
                            actualMismatches.add(Tuples.pair(refId, actualNotExpected.sortThis()));
                        }
                    }
                });
                actualSets.forEachKeyValue((refId, actualPaths) ->
                {
                    if (!expectedSets.containsKey(refId))
                    {
                        actualMismatches.add(Tuples.pair(refId, actualPaths.toSortedList()));
                    }
                });
                Assert.assertEquals("external reference mismatch", expectedMismatches.sortThisBy(Pair::getOne), actualMismatches.sortThisBy(Pair::getOne));
            }
        }

        Assert.assertEquals(expected, actual);
    }
}
