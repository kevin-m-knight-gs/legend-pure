package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjUpdate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class MultiDistributedBinaryGraphDeserializer
{
    protected MultiDistributedBinaryGraphDeserializer()
    {
    }

    public abstract boolean hasClassifier(String classifierId);

    public abstract RichIterable<String> getClassifiers();

    public abstract boolean hasInstance(String classifierId, String instanceId);

    public abstract RichIterable<String> getClassifierInstanceIds(String classifierId);

    public abstract Obj getInstance(String classifierId, String instanceId);

    public abstract ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds);

    public static MultiDistributedBinaryGraphDeserializer fromDeserializers(DistributedBinaryGraphDeserializer... deserializers)
    {
        Objects.requireNonNull(deserializers, "deserializers may not be null");
        return newDeserializer(Lists.immutable.with(deserializers));
    }

    public static MultiDistributedBinaryGraphDeserializer fromDeserializers(Iterable<? extends DistributedBinaryGraphDeserializer> deserializers)
    {
        Objects.requireNonNull(deserializers, "deserializers may not be null");
        return newDeserializer(Lists.immutable.withAll(deserializers));
    }

    public static MultiDistributedBinaryGraphDeserializer fromClassLoader(Iterable<String> metadataNames, ClassLoader classLoader)
    {
        Objects.requireNonNull(metadataNames, "metadata names may not be null");
        Objects.requireNonNull(classLoader, "class loader may not be null");
        return fromFileReader(metadataNames, FileReaders.fromClassLoader(classLoader));
    }

    public static MultiDistributedBinaryGraphDeserializer fromFileReader(Iterable<String> metadataNames, FileReader fileReader)
    {
        Objects.requireNonNull(metadataNames, "metadata names may not be null");
        Objects.requireNonNull(fileReader, "file reader may not be null");
        return fromDeserializers(Iterate.collect(metadataNames, mn -> DistributedBinaryGraphDeserializer.fromFileReader(mn, fileReader), Lists.mutable.empty()));
    }

    private static MultiDistributedBinaryGraphDeserializer newDeserializer(ImmutableList<DistributedBinaryGraphDeserializer> deserializers)
    {
        deserializers.forEach(d -> Objects.requireNonNull(d, "deserializer may not be null"));
        switch (deserializers.size())
        {
            case 0:
            {
                return new EmptyDeserializer();
            }
            case 1:
            {
                return new SingleDeserializer(deserializers.get(0));
            }
            default:
            {
                return new ManyDeserializer(deserializers);
            }
        }
    }

    private static class EmptyDeserializer extends MultiDistributedBinaryGraphDeserializer
    {
        private EmptyDeserializer()
        {
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return false;
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return Sets.immutable.empty();
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return false;
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return Sets.immutable.empty();
        }

        @Override
        public Obj getInstance(String classifierId, String instanceId)
        {
            throw new RuntimeException("Unknown instance: classifier='" + classifierId + "', id='" + instanceId + "'");
        }

        @Override
        public ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds)
        {
            MutableSet<String> instanceIdSet = Sets.mutable.withAll(instanceIds);
            switch (instanceIdSet.size())
            {
                case 0:
                {
                    return Lists.immutable.empty();
                }
                case 1:
                {
                    throw new RuntimeException("Unknown instance: classifier='" + classifierId + "', id='" + instanceIdSet.getAny() + "'");
                }
                default:
                {
                    throw new RuntimeException(instanceIdSet.toSortedList().makeString("Unknown instance: classifier='" + classifierId + "', ids='", "', '", "'"));
                }
            }
        }
    }

    private static class SingleDeserializer extends MultiDistributedBinaryGraphDeserializer
    {
        private final DistributedBinaryGraphDeserializer deserializer;

        private SingleDeserializer(DistributedBinaryGraphDeserializer deserializer)
        {
            this.deserializer = deserializer;
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return this.deserializer.hasClassifier(classifierId);
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return this.deserializer.getClassifiers();
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return this.deserializer.hasInstance(classifierId, instanceId);
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return this.deserializer.getClassifierInstanceIds(classifierId);
        }

        @Override
        public Obj getInstance(String classifierId, String instanceId)
        {
            Obj obj = this.deserializer.getInstance(classifierId, instanceId);
            if (obj instanceof ObjUpdate)
            {
                throw new RuntimeException("Cannot find main definition for instance: classifier='" + classifierId + "', id='" + instanceId + "'");
            }
            return obj;
        }

        @Override
        public ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds)
        {
            ListIterable<Obj> objs = this.deserializer.getInstances(classifierId, instanceIds);
            if (objs.anySatisfy(o -> o instanceof ObjUpdate))
            {
                MutableList<String> invalidIds = objs.collectIf(o -> o instanceof ObjUpdate, Obj::getIdentifier, Lists.mutable.empty());
                boolean many = invalidIds.size() > 1;
                StringBuilder builder = new StringBuilder("Cannot find main definition for ").append(many ? "instances: " : "instance: ");
                builder.append("classifier'").append(classifierId).append("', id");
                if (many)
                {
                    invalidIds.sortThis().appendString(builder, "s: '", "', '", "'");
                }
                else
                {
                    builder.append(": '").append(invalidIds.get(0)).append("'");
                }
                throw new RuntimeException(builder.toString());
            }
            return objs;
        }
    }

    private static class ManyDeserializer extends MultiDistributedBinaryGraphDeserializer
    {
        private final ImmutableList<DistributedBinaryGraphDeserializer> deserializers;

        private ManyDeserializer(ImmutableList<DistributedBinaryGraphDeserializer> deserializers)
        {
            this.deserializers = deserializers;
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return this.deserializers.anySatisfy(d -> d.hasClassifier(classifierId));
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return this.deserializers.flatCollect(DistributedBinaryGraphDeserializer::getClassifiers, Sets.mutable.empty());
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return this.deserializers.anySatisfy(d -> d.hasInstance(classifierId, instanceId));
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return this.deserializers.flatCollect(d -> d.getClassifierInstanceIds(classifierId), Sets.mutable.empty());
        }

        @Override
        public Obj getInstance(String classifierId, String instanceId)
        {
            Obj main = null;
            List<ObjUpdate> updates = Lists.mutable.withInitialCapacity(this.deserializers.size() - 1);
            for (DistributedBinaryGraphDeserializer deserializer : this.deserializers)
            {
                Obj obj = deserializer.getInstanceIfPresent(classifierId, instanceId);
                if (obj != null)
                {
                    if (obj instanceof ObjUpdate)
                    {
                        updates.add((ObjUpdate) obj);
                    }
                    else if (main == null)
                    {
                        main = obj;
                    }
                    else
                    {
                        throw new RuntimeException("Multiple main definitions for instance: classifier='" + classifierId + "', id='" + instanceId + "'");
                    }
                }
            }
            if (main == null)
            {
                String message = updates.isEmpty() ?
                        "Unknown instance: classifier='" + classifierId + "', id='" + instanceId + "'" :
                        "Cannot find main definition for instance: classifier='" + classifierId + "', id='" + instanceId + "'";
                throw new RuntimeException(message);
            }
            return updates.isEmpty() ? main : main.applyUpdates(updates);
        }

        @Override
        public ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds)
        {
            Set<String> instanceIdSet = (instanceIds instanceof Set) ? (Set<String>) instanceIds : Sets.mutable.withAll(instanceIds);
            if (instanceIdSet.isEmpty())
            {
                return Lists.immutable.empty();
            }

            MutableMap<String, List<Obj>> objsById = Maps.mutable.withInitialCapacity(instanceIdSet.size());
            this.deserializers.asLazy().flatCollect(d -> d.getInstancesIfPresent(classifierId, instanceIdSet)).forEach(o -> objsById.getIfAbsentPut(o.getIdentifier(), Lists.mutable::empty).add(o));
            if (instanceIdSet.size() > objsById.size())
            {
                boolean many = (instanceIdSet.size() - objsById.size()) > 1;
                StringBuilder builder = new StringBuilder(many ? "Unknown instances: " : "Unknown instance: ");
                builder.append("classifier='").append(classifierId).append("', id");
                if (many)
                {
                    Iterate.reject(instanceIdSet, objsById::containsKey, Lists.mutable.empty()).sortThis().appendString(builder, "s='", "', '", "'");
                }
                else
                {
                    builder.append("='").append(Iterate.detect(instanceIdSet, id -> !objsById.containsKey(id))).append("'");
                }
                throw new RuntimeException(builder.toString());
            }
            return objsById.valuesView().collect(this::reduceObjs, Lists.mutable.withInitialCapacity(objsById.size()));
        }

        private Obj reduceObjs(List<Obj> objs)
        {
            if (objs.size() == 1)
            {
                Obj result = objs.get(0);
                if (result instanceof ObjUpdate)
                {
                    throw new RuntimeException("Cannot find main definition for instance: classifier='" + result.getClassifier() + "', id='" + result.getIdentifier() + "'");
                }
                return result;
            }

            Obj main = null;
            List<ObjUpdate> updates = Lists.mutable.withInitialCapacity(objs.size() - 1);
            for (Obj obj : objs)
            {
                if (obj instanceof ObjUpdate)
                {
                    updates.add((ObjUpdate) obj);
                }
                else if (main == null)
                {
                    main = obj;
                }
                else
                {
                    throw new RuntimeException("Multiple main definitions for instance: classifier='" + obj.getClassifier() + "', id='" + obj.getIdentifier() + "'");
                }
            }
            if (main == null)
            {
                throw new RuntimeException("Cannot find main definition for instance: classifier='" + objs.get(0).getClassifier() + "', id='" + objs.get(0).getIdentifier() + "'");
            }
            return main.applyUpdates(updates);
        }
    }
}
