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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Enum)
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference(leftPath)
                .withExternalReference(leftPath + ".properties['name']")
                .withExternalReference(rightPath)
                .withExternalReference(rightPath + ".properties['id']")
                .withExternalReference("meta::pure::functions::boolean::equal_Any_MANY__Any_MANY__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::Left")
                .withExternalReference("test::model::Right")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::profiles::doc.p_stereotypes[value='deprecated']")
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='doc']")
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='todo']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::boolean::not_Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::at_T_MANY__Integer_1__T_1_")
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_MANY__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_")
                .withExternalReference("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_")
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_")
                .withExternalReference("meta::pure::functions::string::joinStrings_String_MANY__String_1__String_1__String_1__String_1_")
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.OneMany)
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_")
                .withExternalReference("meta::pure::milestoning::BusinessDateMilestoning")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='businesstemporal']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning2")
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']")
                .withExternalReference("test::model::ClassWithMilestoning3")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']")
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
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.OneMany)
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_")
                .withExternalReference("meta::pure::milestoning::ProcessingDateMilestoning")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='processingtemporal']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1")
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning3")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']")
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
                .withExternalReference(M3Paths.Date)
                .withExternalReference(M3Paths.Class)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::functions::collection::first_T_MANY__T_$0_1$_")
                .withExternalReference("meta::pure::milestoning::BiTemporalMilestoning")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningdateproperty']")
                .withExternalReference("meta::pure::profiles::temporal.p_stereotypes[value='bitemporal']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1")
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning2")
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1")
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning2")
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning1")
                .withExternalReference("test::model::ClassWithMilestoning1.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning3")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']")
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
                .withExternalReference(M3Paths.Property)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.QualifiedProperty)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_")
                .withExternalReference("meta::pure::profiles::milestoning.p_stereotypes[value='generatedmilestoningproperty']")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::ClassWithMilestoning2")
                .withExternalReference("test::model::ClassWithMilestoning2.properties['processingDate']")
                .withExternalReference("test::model::ClassWithMilestoning3")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['processingDate']")
                .withExternalReference("test::model::ClassWithMilestoning3.properties['businessDate']")
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
                .withExternalReference("meta::pure::profiles::doc.p_tags[value='doc']")
                .withExternalReference("meta::pure::test::pct::PCT.p_stereotypes[value='function']")
                .withExternalReference("meta::pure::test::pct::PCT.p_tags[value='grammarDoc']")
                .withExternalReference("system::imports::import__platform_pure_grammar_functions_lang_compare_pure_1")
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
                .withExternalReference(M3Paths.Function)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::functions::collection::isEmpty_Any_$0_1$__Boolean_1_")
                .withExternalReference("meta::pure::functions::collection::map_T_m__Function_1__V_m_")
                .withExternalReference("meta::pure::functions::lang::eval_Function_1__T_n__V_m_")
                .withExternalReference("meta::pure::functions::lang::if_Boolean_1__Function_1__Function_1__T_m_")
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_")
                .withExternalReference("meta::pure::functions::string::toString_Any_1__String_1_")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
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
                .withExternalReference(M3Paths.Type + ".properties['name']")
                .withExternalReference(M3Paths.Unit)
                .withExternalReference(M3Paths.Unit + ".properties['measure']")
                .withExternalReference(M3Paths.ZeroOne)
                .withExternalReference("meta::pure::functions::lang::letFunction_String_1__T_m__T_m_")
                .withExternalReference("meta::pure::functions::meta::elementToPath_PackageableElement_1__String_1_")
                .withExternalReference("meta::pure::functions::multiplicity::toOne_T_MANY__T_1_")
                .withExternalReference("meta::pure::functions::string::plus_String_MANY__String_1_")
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("test::model::Mass.nonCanonicalUnits['Pound']")
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
                .withExternalReference(M3Paths.Any)
                .withExternalReference(M3Paths.ConcreteFunctionDefinition)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Package)
                .withExternalReference(M3Paths.Package + ".properties['children']")
                .withExternalReference(M3Paths.PackageableElement)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.String)
                .withExternalReference(M3Paths.ZeroMany)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
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
                .withExternalReference(M3Paths.DataType)
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
                .withExternalReference(M3Paths.DataType)
                .withExternalReference(M3Paths.Float)
                .withExternalReference(M3Paths.Integer)
                .withExternalReference(M3Paths.LambdaFunction)
                .withExternalReference(M3Paths.Measure)
                .withExternalReference(M3Paths.Number)
                .withExternalReference(M3Paths.PureOne)
                .withExternalReference(M3Paths.Unit)
                .withExternalReference("system::imports::import__ref_test_test_pure_1")
                .withExternalReference("test::model")
                .withExternalReference("meta::pure::functions::math::times_Number_MANY__Number_1_")
                .build();
        assertMetadata(expected, mass);
    }

    private void assertMetadata(ConcreteElementMetadata expected, CoreInstance concreteElement)
    {
        ConcreteElementMetadata actual = generator.generateMetadata(concreteElement);
        Assert.assertEquals(expected, actual);
    }
}
