/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.data;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.ImmutableDataManipulator;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.data.SpongeDataManager;
import org.spongepowered.common.interfaces.data.IMixinActiveDataHolder;
import org.spongepowered.common.interfaces.data.IMixinCustomDataHolder;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@Mixin({TileEntity.class, Entity.class})
public abstract class MixinCustomDataHolder implements DataHolder, IMixinCustomDataHolder {

    private List<DataManipulator<?, ?>> manipulators = Lists.newArrayList();

    @SuppressWarnings("rawtypes")
    @Override
    public DataTransactionResult offerCustom(DataManipulator<?, ?> manipulator, MergeFunction function) {
        @Nullable DataManipulator<?, ?> existingManipulator = null;
        for (DataManipulator<?, ?> existing : this.manipulators) {
            if (manipulator.getClass().isInstance(existing)) {
                existingManipulator = existing;
                break;
            }
        }
        if (this instanceof IMixinActiveDataHolder && ((IMixinActiveDataHolder) this).isActive()) {
            @Nullable final DataManipulator<?, ?> existingManipulator0 = existingManipulator;
            return SpongeDataManager.getInstance().offer(this, existingManipulator, manipulator, function,
                    o -> update(existingManipulator0, o.orElse(null)));
        } else {
            return update(existingManipulator, checkNotNull(function).merge(existingManipulator, manipulator));
        }
    }

    private DataTransactionResult update(@Nullable DataManipulator<?, ?> oldManipulator, @Nullable DataManipulator<?, ?> newManipulator) {
        final DataTransactionResult.Builder builder = DataTransactionResult.builder();
        if (oldManipulator != null) {
            builder.replace(oldManipulator.getValues());
            this.manipulators.remove(oldManipulator);
            if (newManipulator == null) {
                removeCustomFromNbt(oldManipulator);
            }
        }
        if (newManipulator != null) {
            builder.success(newManipulator.getValues());
            this.manipulators.add(newManipulator);
        }
        return builder.result(DataTransactionResult.Type.SUCCESS)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends DataManipulator<?, ?>> Optional<T> getCustom(Class<T> customClass) {
        for (DataManipulator<?, ?> existing : this.manipulators) {
            if (customClass.isInstance(existing)) {
                return Optional.of((T) existing.copy());
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataTransactionResult removeCustom(Class<? extends DataManipulator<?, ?>> customClass) {
        @Nullable DataManipulator<?, ?> manipulator = null;
        for (DataManipulator<?, ?> existing : this.manipulators) {
            if (customClass.isInstance(existing)) {
                manipulator = existing;
            }
        }
        if (manipulator != null) {
            if (this instanceof IMixinActiveDataHolder && ((IMixinActiveDataHolder) this).isActive()) {
                @Nullable final DataManipulator<?, ?> manipulator0 = manipulator;
                return SpongeDataManager.getInstance().remove(this, (Class) customClass, o -> {
                    return update(manipulator0, (DataManipulator<?, ?>) o.orElse(null));
                });
            } else {
                return update(manipulator, null);
            }
        } else {
            return DataTransactionResult.failNoData();
        }
    }

    @Override
    public boolean hasManipulators() {
        return !this.manipulators.isEmpty();
    }

    @Override
    public boolean supportsCustom(Key<?> key) {
        return this.manipulators.stream()
                .filter(manipulator -> manipulator.supports(key))
                .findFirst()
                .isPresent();
    }

    @Override
    public <E> Optional<E> getCustom(Key<? extends BaseValue<E>> key) {
        return this.manipulators.stream()
                .filter(manipulator -> manipulator.supports(key))
                .findFirst()
                .flatMap(supported -> supported.get(key));
    }

    @Override
    public <E, V extends BaseValue<E>> Optional<V> getCustomValue(Key<V> key) {
        return this.manipulators.stream()
                .filter(manipulator -> manipulator.supports(key))
                .findFirst()
                .flatMap(supported -> supported.getValue(key));
    }

    @Override
    public List<DataManipulator<?, ?>> getCustomManipulators() {
        return this.manipulators.stream().map(DataManipulator::copy).collect(Collectors.toList());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <E> DataTransactionResult offerCustom(Key<? extends BaseValue<E>> key, E value) {
        if (this instanceof IMixinActiveDataHolder && ((IMixinActiveDataHolder) this).isActive()) {
            return SpongeDataManager.getInstance().offer(this, key, value);
        }
        for (DataManipulator<?, ?> manipulator : this.manipulators) {
            if (manipulator.supports(key)) {
                final DataTransactionResult.Builder builder = DataTransactionResult.builder();
                builder.replace(((Value) manipulator.getValue((Key) key).get()).asImmutable());
                manipulator.set(key, value);
                builder.success(((Value) manipulator.getValue((Key) key).get()).asImmutable());
                return builder.result(DataTransactionResult.Type.SUCCESS).build();
            }
        }
        return DataTransactionResult.failNoData();
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataTransactionResult removeCustom(Key<?> key) {
        Optional manipulatorClass = SpongeDataManager.getInstance().getManipulatorClass(key);
        if (manipulatorClass.isPresent()) {
            return removeCustom((Class<? extends DataManipulator<?, ?>>) manipulatorClass.get());
        }
        return DataTransactionResult.failNoData();
    }

}
