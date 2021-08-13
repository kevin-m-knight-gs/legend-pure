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

package org.finos.legend.pure.runtime.java.compiled.metadata;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.Counter;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.type.EnumProcessor;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryGraphDeserializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.MultiDistributedBinaryGraphDeserializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Enum;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.EnumRef;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjRef;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Primitive;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValue;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueMany;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueOne;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueVisitor;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValue;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValueConsumer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValueVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Set;

public class MetadataLazy implements Metadata
{
    private static final PropertyValueVisitor<Object> VALUES_VISITOR = new PropertyValueVisitor<Object>()
    {
        @Override
        public Object visit(PropertyValueMany many)
        {
            return many.getValues();
        }

        @Override
        public Object visit(PropertyValueOne one)
        {
            return one.getValue();
        }
    };

    private final RValueVisitor<Object> valueToObjectVisitor = new RValueVisitor<Object>()
    {
        @Override
        public Object visit(Primitive primitive)
        {
            return primitive.getValue();
        }

        @Override
        public Object visit(ObjRef ref)
        {
            return toJavaObject(ref.getClassifierId(), ref.getId());
        }

        @Override
        public Object visit(EnumRef enumRef)
        {
            return getEnum(enumRef.getEnumerationId(), enumRef.getEnumName());
        }
    };

    private final ClassLoader classLoader;
    private final MultiDistributedBinaryGraphDeserializer deserializer;
    private final ConcurrentMutableMap<String, Constructor<? extends CoreInstance>> constructors = ConcurrentHashMap.newMap();
    private final ConcurrentMutableMap<String, ConcurrentMutableMap<String, CoreInstance>> instanceCache = ConcurrentHashMap.newMap();
    private final ConcurrentMutableMap<String, MapIterable<String, CoreInstance>> enumCache = ConcurrentHashMap.newMap();

    private volatile Constructor<? extends CoreInstance> enumConstructor = null; //NOSONAR we actually want to protect the pointer

    private MetadataLazy(ClassLoader classLoader, MultiDistributedBinaryGraphDeserializer deserializer)
    {
        this.classLoader = classLoader;
        this.deserializer = deserializer;
    }

    @Override
    public void startTransaction()
    {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void commitTransaction()
    {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void rollbackTransaction()
    {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public CoreInstance getMetadata(String classifier, String id)
    {
        return hasClassifier(classifier) ? toJavaObject(classifier, id) : null;
    }

    @Override
    public MapIterable<String, CoreInstance> getMetadata(String classifier)
    {
        if (!hasClassifier(classifier))
        {
            return null;
        }
        loadAllClassifierInstances(classifier);
        return getClassifierInstanceCache(classifier).asUnmodifiable();
    }

    @Override
    public CoreInstance getEnum(String enumerationName, String enumName)
    {
        MapIterable<String, CoreInstance> enumerationCache = this.enumCache.getIfAbsentPutWithKey(enumerationName, this::indexEnumerationValues);
        if (enumerationCache == null)
        {
            throw new RuntimeException("Cannot find enum '" + enumName + "' in enumeration '" + enumerationName + "': unknown enumeration");
        }
        return enumerationCache.get(enumName);
    }

    private MapIterable<String, CoreInstance> indexEnumerationValues(String enumerationId)
    {
        MapIterable<String, CoreInstance> enums = getMetadata(enumerationId);
        return (enums == null) ? null : enums.groupByUniqueKey(CoreInstance::getName, Maps.mutable.withInitialCapacity(enums.size()));
    }

    public Object valueToObject(RValue value)
    {
        return (value == null) ? null : value.visit(this.valueToObjectVisitor);
    }

    @SuppressWarnings("unchecked")
    public RichIterable<Object> valuesToObjects(Object value)
    {
        if (value == null)
        {
            return Lists.immutable.empty();
        }

        if (value instanceof RValue)
        {
            return Lists.mutable.with(valueToObject((RValue) value));
        }

        return valuesToObjects((ListIterable<RValue>) value);
    }

    public RichIterable<Object> valuesToObjects(ListIterable<RValue> values)
    {
        int size = (values == null) ? 0 : values.size();
        if (size == 0)
        {
            return Lists.immutable.empty();
        }
        if (size == 1)
        {
            return Lists.mutable.with(valueToObject(values.get(0)));
        }

        MutableMap<String, MutableSet<ObjRef>> objRefsByClassifier = Maps.mutable.empty();
        Counter objRefCounter = new Counter();
        values.forEach(new RValueConsumer()
        {
            @Override
            protected void accept(Primitive primitive)
            {
            }

            @Override
            protected void accept(ObjRef objRef)
            {
                objRefsByClassifier.getIfAbsentPut(objRef.getClassifierId(), Sets.mutable::empty).add(objRef);
                objRefCounter.increment();
            }

            @Override
            protected void accept(EnumRef enumRef)
            {
            }
        });
        if (objRefsByClassifier.isEmpty())
        {
            return values.collect(this::valueToObject);
        }

        MutableMap<ObjRef, CoreInstance> objectByRef = Maps.mutable.withInitialCapacity(objRefCounter.getCount());
        objRefsByClassifier.forEach((classifier, objRefs) ->
        {
            MutableList<String> idsToDeserialize = Lists.mutable.withInitialCapacity(objRefs.size());
            ConcurrentMutableMap<String, CoreInstance> classifierCache = getClassifierInstanceCache(classifier);
            objRefs.forEach(objRef ->
            {
                String id = objRef.getId();
                CoreInstance cachedInstance = classifierCache.get(id);
                if (cachedInstance == null)
                {
                    idsToDeserialize.add(id);
                }
                else
                {
                    objectByRef.put(objRef, cachedInstance);
                }
            });
            if (idsToDeserialize.notEmpty())
            {
                ListIterable<Obj> deserialized = getInstances(classifier, idsToDeserialize);
                deserialized.forEach(obj ->
                {
                    CoreInstance cachedInstance = classifierCache.getIfAbsentPut(obj.getIdentifier(), () -> newInstance(classifier, obj));
                    objectByRef.put(new ObjRef(obj.getClassifier(), obj.getIdentifier()), cachedInstance);
                });
            }
        });
        return values.collectWith(RValue::visit, new RValueVisitor<Object>()
        {
            @Override
            public Object visit(Primitive primitive)
            {
                return primitive.getValue();
            }

            @Override
            public Object visit(ObjRef objRef)
            {
                return objectByRef.get(objRef);
            }

            @Override
            public Object visit(EnumRef enumRef)
            {
                return getEnum(enumRef.getEnumerationId(), enumRef.getEnumName());
            }
        });
    }

    public ImmutableMap<String, Object> buildMap(Obj instance)
    {
        return instance.getPropertyValues().toMap(PropertyValue::getProperty, pv -> pv.visit(VALUES_VISITOR)).toImmutable();
    }

    private boolean hasClassifier(String classifier)
    {
        return this.deserializer.hasClassifier(classifier);
    }

    private RichIterable<String> getClassifierInstanceIds(String classifier)
    {
        return this.deserializer.getClassifierInstanceIds(classifier);
    }

    private Obj getInstance(String classifier, String id)
    {
        return this.deserializer.getInstance(classifier, id);
    }

    private ListIterable<Obj> getInstances(String classifier, Iterable<String> instanceIds)
    {
        return this.deserializer.getInstances(classifier, instanceIds);
    }

    private void loadAllClassifierInstances(String classifier)
    {
        RichIterable<String> instanceIds = getClassifierInstanceIds(classifier);
        ConcurrentMutableMap<String, CoreInstance> classifierCache = getClassifierInstanceCache(classifier);
        int notLoadedCount = instanceIds.size() - classifierCache.size();
        if (notLoadedCount > 0)
        {
            MutableList<String> instanceIdsToLoad = instanceIds.reject(classifierCache::containsKey, Lists.mutable.withInitialCapacity(notLoadedCount));
            ListIterable<Obj> objs = getInstances(classifier, instanceIdsToLoad);
            objs.forEach(obj -> classifierCache.getIfAbsentPut(obj.getIdentifier(), () -> newInstance(classifier, obj)));
        }
    }

    private CoreInstance toJavaObject(String classifier, String id)
    {
        return getClassifierInstanceCache(classifier).getIfAbsentPut(id, () -> newInstance(classifier, id));
    }

    private ConcurrentMutableMap<String, CoreInstance> getClassifierInstanceCache(String classifier)
    {
        return this.instanceCache.getIfAbsentPut(classifier, ConcurrentHashMap::newMap);
    }

    private CoreInstance newInstance(String classifier, String id)
    {
        Obj obj = getInstance(classifier, id);
        return newInstance(classifier, obj);
    }

    private CoreInstance newInstance(String classifier, Obj obj)
    {
        Constructor<? extends CoreInstance> constructor = getConstructor(classifier, obj);
        try
        {
            return constructor.newInstance(obj, this);
        }
        catch (InvocationTargetException | InstantiationException | IllegalAccessException e)
        {
            Throwable cause = (e instanceof InvocationTargetException) ? e.getCause() : e;
            StringBuilder builder = new StringBuilder("Error instantiating ").append(obj).append(" (instance of ").append(classifier).append(")");
            String eMessage = cause.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), cause);
        }
    }

    private Constructor<? extends CoreInstance> getConstructor(String classifier, Obj obj)
    {
        if (obj instanceof Enum)
        {
            Constructor<? extends CoreInstance> constructor = this.enumConstructor;
            if (constructor == null)
            {
                synchronized (this)
                {
                    constructor = this.enumConstructor;
                    if (constructor == null)
                    {
                        this.enumConstructor = constructor = getLazyImplEnumConstructor();
                    }
                }
            }
            return constructor;
        }
        return this.constructors.getIfAbsentPutWithKey(classifier, this::getLazyImplClassConstructor);
    }

    @SuppressWarnings("unchecked")
    private Constructor<? extends CoreInstance> getLazyImplClassConstructor(String classifier)
    {
        String lazyImplClassName = JavaPackageAndImportBuilder.buildLazyImplClassReferenceFromUserPath(classifier);
        try
        {
            Class<? extends CoreInstance> cls = (Class<? extends CoreInstance>) this.classLoader.loadClass(lazyImplClassName);
            return cls.getConstructor(Obj.class, MetadataLazy.class);
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException("Error getting constructor for " + classifier, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Constructor<? extends CoreInstance> getLazyImplEnumConstructor()
    {
        String lazyImplEnumName = JavaPackageAndImportBuilder.rootPackage() + '.' + EnumProcessor.ENUM_LAZY_CLASS_NAME;
        try
        {
            Class<? extends CoreInstance> cls = (Class<? extends CoreInstance>) this.classLoader.loadClass(lazyImplEnumName);
            Constructor<? extends CoreInstance> constructor = cls.getDeclaredConstructor(Obj.class, MetadataLazy.class);
            constructor.setAccessible(true);
            return constructor;
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException("Error getting constructor for " + lazyImplEnumName);
        }
    }

    public static MetadataLazy newMetadata(ClassLoader classLoader, DistributedBinaryGraphDeserializer deserializer)
    {
        Objects.requireNonNull(classLoader, "class loader may not be null");
        Objects.requireNonNull(deserializer, "deserializer may not be null");
        return new MetadataLazy(classLoader, MultiDistributedBinaryGraphDeserializer.fromDeserializers(deserializer));
    }

    public static MetadataLazy newMetadata(ClassLoader classLoader, Iterable<? extends DistributedBinaryGraphDeserializer> deserializers)
    {
        Objects.requireNonNull(classLoader, "class loader may not be null");
        Objects.requireNonNull(deserializers, "deserializers may not be null");
        ImmutableList<DistributedBinaryGraphDeserializer> deserializerList = Lists.immutable.withAll(deserializers);
        if (deserializerList.isEmpty())
        {
            throw new IllegalArgumentException("deserializers are required");
        }
        return new MetadataLazy(classLoader, MultiDistributedBinaryGraphDeserializer.fromDeserializers(deserializerList));
    }

    public static MetadataLazy fromClassLoader(ClassLoader classLoader)
    {
        Objects.requireNonNull(classLoader, "class loader may not be null");
        DistributedBinaryGraphDeserializer deserializer = DistributedBinaryGraphDeserializer.fromClassLoader(classLoader);
        return new MetadataLazy(classLoader, MultiDistributedBinaryGraphDeserializer.fromDeserializer(deserializer));
    }

    public static MetadataLazy fromClassLoader(ClassLoader classLoader, String metadataName)
    {
        Objects.requireNonNull(classLoader, "class loader may not be null");
        Objects.requireNonNull(metadataName, "metadata name may not be null");
        MultiDistributedBinaryGraphDeserializer deserializer = MultiDistributedBinaryGraphDeserializer.fromClassLoader(Lists.immutable.with(metadataName), classLoader);
        return new MetadataLazy(classLoader, deserializer);
    }

    public static MetadataLazy fromClassLoader(ClassLoader classLoader, Iterable<String> metadataNames)
    {
        Objects.requireNonNull(classLoader, "class loader may not be null");
        Objects.requireNonNull(metadataNames, "metadata names may not be null");
        Set<String> metadataNamesSet = (metadataNames instanceof Set) ? (Set<String>) metadataNames : Sets.mutable.withAll(metadataNames);
        if (metadataNamesSet.isEmpty())
        {
            throw new IllegalArgumentException("metadata names are required");
        }
        if (metadataNamesSet.contains(null))
        {
            throw new NullPointerException("metadata name may not be null");
        }
        MultiDistributedBinaryGraphDeserializer deserializer = MultiDistributedBinaryGraphDeserializer.fromClassLoader(metadataNamesSet, classLoader);
        return new MetadataLazy(classLoader, deserializer);
    }
}
