/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.property;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectContext;
import org.apache.ignite.internal.processors.query.GridQueryProperty;
import org.apache.ignite.internal.processors.query.PropertyMembership;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.jetbrains.annotations.Nullable;

/**
 * Description of type property.
 */
public class QueryClassProperty implements GridQueryProperty {
    /** */
    private final QueryPropertyAccessor accessor;

    /** */
    private final PropertyMembership membership;

    /** */
    private QueryClassProperty parent;

    /** */
    private final String name;

    /** */
    private final CacheObjectContext coCtx;

    /** */
    private final boolean notNull;

    /**
     * Constructor.
     *
     * @param accessor Way of accessing the property.
     * @param membership Whether the property belongs to the cache entry's key, value or both.
     * @param name Property name.
     * @param notNull {@code true} if null value is not allowed.
     * @param coCtx Cache Object Context.
     */
    public QueryClassProperty(QueryPropertyAccessor accessor, PropertyMembership membership, String name,
        boolean notNull, @Nullable CacheObjectContext coCtx) {
        this.accessor = accessor;

        this.membership = membership;

        this.name = !F.isEmpty(name) ? name : accessor.getPropertyName();

        this.notNull = notNull;

        this.coCtx = coCtx;
    }

    /** {@inheritDoc} */
    @Override public Object value(Object key, Object val) throws IgniteCheckedException {
        Object x = unwrap(membership == PropertyMembership.KEY ? key : val);

        if (parent != null)
            x = parent.value(key, val);

        if (x == null)
            return null;

        return accessor.getValue(x);
    }

    /** {@inheritDoc} */
    @Override public void setValue(Object key, Object val, Object propVal) throws IgniteCheckedException {
        if (membership() != PropertyMembership.VALUE)
            setValue(key, val, propVal, unwrap(key));

        if (membership() != PropertyMembership.KEY)
            setValue(key, val, propVal, unwrap(val));
    }

    /**
     * Sets this property value for the given object.
     *
     * @param key Key.
     * @param val Value.
     * @param propVal Property value.
     * @param x The object to set the property of.
     * @throws IgniteCheckedException If failed.
     */
    private void setValue(Object key, Object val, Object propVal, Object x) throws IgniteCheckedException {
        if (parent != null)
            x = parent.value(key, val);

        if (x == null)
            return;

        accessor.setValue(x, propVal);
    }

    /** {@inheritDoc} */
    @Override public PropertyMembership membership() {
        return membership;
    }

    /**
     * Unwraps cache object, if needed.
     *
     * @param o Object to unwrap.
     * @return Unwrapped object.
     */
    private Object unwrap(Object o) {
        return coCtx == null ? o : o instanceof CacheObject ? ((CacheObject)o).value(coCtx, false) : o;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public Class<?> type() {
        return accessor.getType();
    }

    /**
     * @param parent Parent property if this is embeddable element.
     */
    public void parent(QueryClassProperty parent) {
        this.parent = parent;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(QueryClassProperty.class, this);
    }

    /** {@inheritDoc} */
    @Override public GridQueryProperty parent() {
        return parent;
    }

    /** {@inheritDoc} */
    @Override public boolean notNull() {
        return notNull;
    }

    /** {@inheritDoc} */
    @Override public Object defaultValue() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public int precision() {
        return -1;
    }

    /** {@inheritDoc} */
    @Override public int scale() {
        return -1;
    }
}
