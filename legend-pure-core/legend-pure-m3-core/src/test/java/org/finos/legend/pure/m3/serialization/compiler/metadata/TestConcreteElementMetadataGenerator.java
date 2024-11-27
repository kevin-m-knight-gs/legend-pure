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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Profile;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.NativeFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Measure;
import org.finos.legend.pure.m3.navigation.M3Paths;
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['id'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['name'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.Enum, specialization(path + ".generalizations[0]"))
                .withExternalReference(M3Paths.Enumeration)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Association)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['toLeft'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toRight'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::equal_Any_MANY__Any_MANY__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0]"))
                .withExternalReference("test::model")
                .withExternalReference(leftPath,
                        propFromAssoc(path + ".properties['toRight']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toRight(Integer[1])']"),
                        refUsage(path + ".properties['toLeft'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toRight'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"))
                .withExternalReference(rightPath,
                        propFromAssoc(path + ".properties['toLeft']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toLeft(String[1])']"),
                        refUsage(path + ".properties['toLeft'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".properties['toRight'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['r'].genericType", "rawType"))
                .withExternalReference(leftPath + ".properties['name']",
                        application(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .withExternalReference(rightPath + ".properties['id']",
                        application(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .build();
        assertMetadata(expectedLR, leftRight);

        Class<?> left = getCoreInstance(leftPath);
        ConcreteElementMetadata expectedLeft = ConcreteElementMetadata.builder()
                .withPath(leftPath)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(left.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Property, refUsage(leftPath + ".properties['name'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("test::model")
                .build();
        assertMetadata(expectedLeft, left);

        Class<?> right = getCoreInstance(rightPath);
        ConcreteElementMetadata expectedRight = ConcreteElementMetadata.builder()
                .withPath(rightPath)
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(right.getSourceInformation())
                .withReferenceIdVersion(referenceIds.getDefaultVersion())
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.Property, refUsage(rightPath + ".properties['id'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.Profile)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['leftCount'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['rightCount'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference("test::model")
                .withExternalReference("test::model::Left",
                        refUsage(path + ".generalizations[0].general", "rawType"),
                        specialization(path + ".generalizations[0]"))
                .withExternalReference("test::model::Right",
                        refUsage(path + ".generalizations[1].general", "rawType"),
                        specialization(path + ".generalizations[1]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['deprecated'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['alsoDeprecated'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['date'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("test::model")
                .withExternalReference("meta::pure::profiles::doc.p_stereotypes[value='deprecated']",
                        modelElement(path),
                        modelElement(path + ".properties['deprecated']"),
                        modelElement(path + ".properties['alsoDeprecated']"))
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='doc']",
                        modelElement(path),
                        modelElement(path + ".properties['alsoDeprecated']"),
                        modelElement(path + ".properties['date']"))
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='todo']", modelElement(path + ".properties['date']"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['propT'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['propV'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".properties['names'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['title'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("meta::pure::functions::boolean::not_Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::collection::at_T_MANY__Integer_1__T_1_",
                        application(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_MANY__Boolean_1_",
                        application(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_",
                        application(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0]"))
                .withExternalReference("meta::pure::functions::string::joinStrings_String_MANY__String_1__String_1__String_1__String_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1]"))
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_",
                        application(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass2'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass3'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['businessDate'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['milestoning'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass2AllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass3AllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.OneMany)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::milestoning::BusinessDateMilestoning",
                        refUsage(path + ".properties['milestoning'].classifierGenericType.typeArguments[1]", "rawType"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass2AllVersions']"),
                        modelElement(path + ".properties['toClass3AllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])']"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        modelElement(path + ".properties['businessDate']"),
                        modelElement(path + ".properties['milestoning']"))
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='businesstemporal']",
                        modelElement(path))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning2",
                        refUsage(path + ".originalMilestonedProperties['toClass2'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass2AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3",
                        refUsage(path + ".originalMilestonedProperties['toClass3'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass3AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass1'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass3'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['milestoning'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['processingDate'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass1AllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass3AllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.OneMany)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::milestoning::ProcessingDateMilestoning",
                        refUsage(path + ".properties['milestoning'].classifierGenericType.typeArguments[1]", "rawType"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass1AllVersions']"),
                        modelElement(path + ".properties['toClass3AllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])']"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        modelElement(path + ".properties['milestoning']"),
                        modelElement(path + ".properties['processingDate']"))
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='processingtemporal']",
                        modelElement(path))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1",
                        refUsage(path + ".originalMilestonedProperties['toClass1'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3",
                        refUsage(path + ".originalMilestonedProperties['toClass3'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass3AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass3(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass1'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass2'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['businessDate'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['milestoning'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['processingDate'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass1AllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass2AllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass1()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass1()'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2()'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("meta::pure::functions::collection::first_T_MANY__T_$0_1$_",
                        application(path + ".qualifiedProperties[id='toClass1()'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2()'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::milestoning::BiTemporalMilestoning",
                        refUsage(path + ".properties['milestoning'].classifierGenericType.typeArguments[1]", "rawType"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass1AllVersions']"),
                        modelElement(path + ".properties['toClass2AllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1()']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2()']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])']"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']",
                        modelElement(path + ".properties['businessDate']"),
                        modelElement(path + ".properties['milestoning']"),
                        modelElement(path + ".properties['processingDate']"))
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='bitemporal']",
                        modelElement(path))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1",
                        refUsage(path + ".originalMilestonedProperties['toClass1'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1()'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass1()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning2",
                        refUsage(path + ".originalMilestonedProperties['toClass2'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass2AllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2()'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass2()'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2(Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Association)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass1A'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass2A'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass1AAllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass2AAllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass1A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass1A(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2A(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass1AAllVersions']"),
                        modelElement(path + ".properties['toClass2AAllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1A(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2A(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])']"))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1",
                        propFromAssoc(path + ".properties['toClass2AAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass2A(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass1A'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1AAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass2AAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1A(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2A(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass1A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning2",
                        propFromAssoc(path + ".properties['toClass1AAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass1A(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass2A'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1AAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".properties['toClass2AAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1A(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2A(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass2A(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Association)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass1B'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass3B'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass1BAllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass3BAllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass1B()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass1B()'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1B(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass1BAllVersions']"),
                        modelElement(path + ".properties['toClass3BAllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1B()']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1B(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3B(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])']"))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1",
                        propFromAssoc(path + ".properties['toClass3BAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass3B(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass1B'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1BAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass3BAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B()'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass1B()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[1]"))
                .withExternalReference("test::model::ClassWithMilestoning3",
                        propFromAssoc(path + ".properties['toClass1BAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass1B()']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass1B(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass3B'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass1BAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".properties['toClass3BAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B()'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1B(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass1B()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.AggregationKind + ".values['None']")
                .withExternalReference(M3Paths.Association)
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property,
                        refUsage(path + ".originalMilestonedProperties['toClass2C'].classifierGenericType", "rawType"),
                        refUsage(path + ".originalMilestonedProperties['toClass3C'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass2CAllVersions'].classifierGenericType", "rawType"),
                        refUsage(path + ".properties['toClass3CAllVersions'].classifierGenericType", "rawType"))
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_",
                        application(path + ".qualifiedProperties[id='toClass2C()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_",
                        application(path + ".qualifiedProperties[id='toClass2C()'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2C(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0]"))
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']",
                        modelElement(path + ".properties['toClass2CAllVersions']"),
                        modelElement(path + ".properties['toClass3CAllVersions']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2C()']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2C(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3C(Date[1])']"),
                        modelElement(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])']"))
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning2",
                        propFromAssoc(path + ".properties['toClass3CAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass3C(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass2C'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass2CAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass3CAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C()'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass2C()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[1]"))
                .withExternalReference("test::model::ClassWithMilestoning3",
                        propFromAssoc(path + ".properties['toClass2CAllVersions']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass2C()']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass2C(Date[1])']"),
                        qualPropFromAssoc(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])']"),
                        refUsage(path + ".originalMilestonedProperties['toClass3C'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".properties['toClass2CAllVersions'].classifierGenericType.typeArguments[0]", "rawType"),
                        refUsage(path + ".properties['toClass3CAllVersions'].classifierGenericType.typeArguments[1]", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C()'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2C(Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"),
                        refUsage(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['v_milestone'].genericType", "rawType"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']",
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']",
                        application(path + ".qualifiedProperties[id='toClass2C()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"),
                        application(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]"))
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
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.NativeFunction)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference("meta::pure::functions::lang")
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='doc']", modelElement(path))
                .withExternalReference("meta::pure::test::pct::PCT.p_stereotypes[value='function']", modelElement(path))
                .withExternalReference("meta::pure::test::pct::PCT.p_tags[value='grammarDoc']", modelElement(path))
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
                .withExternalReference(M3Paths.Boolean)
                .withExternalReference(M3Paths.ConcreteFunctionDefinition)
                .withExternalReference(M3Paths.Function,
                        refUsage(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType", "rawType"),
                        refUsage(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"))
                .withExternalReference(M3Paths.LambdaFunction,
                        refUsage(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_",
                        application(path + ".expressionSequence[0].parametersValues[1].parametersValues[0]"))
                .withExternalReference("meta::pure::functions::collection::map_T_m__Function_1__V_m_",
                        application(path + ".expressionSequence[1]"))
                .withExternalReference("meta::pure::functions::lang::eval_Function_1__T_n__V_m_",
                        application(path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_",
                        application(path + ".expressionSequence[0].parametersValues[1]"))
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        application(path + ".expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        application(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0]"))
                .withExternalReference("meta::pure::functions::string::toString_Any_1__String_1_",
                        application(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0]"))
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.ConcreteFunctionDefinition)
                .withExternalReference(M3Paths.Measure)
                .withExternalReference(M3Paths.Package)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.Type + ".properties['name']",
                        application(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0]"),
                        application(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0]"))
                .withExternalReference(M3Paths.Unit)
                .withExternalReference(M3Paths.Unit + ".properties['measure']",
                        application(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0]"))
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_",
                        application(path + ".expressionSequence[0]"),
                        application(path + ".expressionSequence[1]"))
                .withExternalReference("meta::pure::functions::meta::elementToPath_PackageableElement_1__String_1_",
                        application(path + ".expressionSequence[2].parametersValues[0].values[0]"))
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_",
                        application(path + ".expressionSequence[2].parametersValues[0].values[2]"),
                        application(path + ".expressionSequence[2].parametersValues[0].values[4]"))
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_",
                        application(path + ".expressionSequence[2]"))
                .withExternalReference("test::model",
                        refUsage(path + ".expressionSequence[0].parametersValues[1]", "values"))
                .withExternalReference("test::model::Mass.nonCanonicalUnits['Pound']",
                        refUsage(path + ".expressionSequence[1].parametersValues[1]", "values"))
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
                .withExternalReference(M3Paths.Any,
                        refUsage(path + ".classifierGenericType.typeArguments[0].rawType.returnType", "rawType"))
                .withExternalReference(M3Paths.ConcreteFunctionDefinition)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Package)
                .withExternalReference(M3Paths.Package + ".properties['children']",
                        application(path + ".expressionSequence[0].values[4].expressionSequence[0]"))
                .withExternalReference(M3Paths.PackageableElement,
                        refUsage(path + ".expressionSequence[0].values[4].classifierGenericType.typeArguments[0].rawType.returnType", "rawType"))
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("test::model",
                        refUsage(path + ".expressionSequence[0].values[0]", "values"),
                        refUsage(path + ".expressionSequence[0].values[4].expressionSequence[0].parametersValues[0]", "values"))
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
                .withExternalReference(M3Paths.DataType, specialization(path + ".generalizations[0]"))
                .withExternalReference(M3Paths.Measure)
                .withExternalReference(M3Paths.Unit)
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.DataType, specialization(path + ".generalizations[0]"))
                .withExternalReference(M3Paths.Float)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Measure)
                .withExternalReference(M3Paths.Number)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.Unit)
                .withExternalReference("meta::pure::functions::math::times_Number_MANY__Number_1_",
                        application(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0]"),
                        application(path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0]"))
                .withExternalReference("test::model")
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
                MutableMap<String, ExternalReference> expectedByRefId = expected.getExternalReferences().groupByUniqueKey(ExternalReference::getReferenceId, Maps.mutable.empty());
                MutableMap<String, ExternalReference> actualByRefId = actual.getExternalReferences().groupByUniqueKey(ExternalReference::getReferenceId, Maps.mutable.empty());

                expectedByRefId.removeIf((refId, extRef) ->
                {
                    if (extRef.equals(actualByRefId.get(refId)))
                    {
                        actualByRefId.removeKey(refId);
                        return true;
                    }
                    return false;
                });
                Assert.assertEquals("external reference mismatch", expectedByRefId.valuesView().toSortedListBy(ExternalReference::getReferenceId), actualByRefId.valuesView().toSortedListBy(ExternalReference::getReferenceId
                ));
            }
        }

        Assert.assertEquals(expected, actual);
    }

    private static BackReference.Application application(String funcExpr)
    {
        return BackReference.newApplication(funcExpr);
    }

    private static BackReference.ModelElement modelElement(String element)
    {
        return BackReference.newModelElement(element);
    }

    private static BackReference.PropertyFromAssociation propFromAssoc(String property)
    {
        return BackReference.newPropertyFromAssociation(property);
    }

    private static BackReference.QualifiedPropertyFromAssociation qualPropFromAssoc(String qualifiedProperty)
    {
        return BackReference.newQualifiedPropertyFromAssociation(qualifiedProperty);
    }

    private static BackReference.ReferenceUsage refUsage(String owner, String property)
    {
        return refUsage(owner, property, 0);
    }

    private static BackReference.ReferenceUsage refUsage(String owner, String property, int offset)
    {
        return BackReference.newReferenceUsage(owner, property, offset);
    }

    private static BackReference.Specialization specialization(String generalization)
    {
        return BackReference.newSpecialization(generalization);
    }
}
