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

package org.apache.ignite.internal.processors.query.h2.disk;

import java.util.Collection;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.query.h2.H2MemoryTracker;
import org.h2.result.ResultExternal;
import org.h2.store.Data;
import org.h2.value.Value;
import org.h2.value.ValueRow;

/**
 * TODO: Add in-memory buffer with memory tracker.
 * This class is intended for spilling to the disk (disk offloading) unsorted intermediate query results.
 */
public class PlainExternalResult extends AbstractExternalResult {
    /**
     * @param ctx Kernal context.
     * @param memTracker Memory tracker.
     */
    public PlainExternalResult(GridKernalContext ctx, H2MemoryTracker memTracker) {
        super(ctx, memTracker);
    }

    /**
     * @param parent Parent result.
     */
    private PlainExternalResult(AbstractExternalResult parent) {
        super(parent);
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        rewindFile();
    }

    /** {@inheritDoc} */
    @Override public Value[] next() {
        Value[] row = readRowFromFile();

        return row;
    }

    /** {@inheritDoc} */
    @Override public int addRow(Value[] row) {
        Data buff = createDataBuffer(rowSize(row));

        addRowToBuffer(row, buff);

        writeBufferToFile(buff);

        return ++size;
    }

    /** {@inheritDoc} */
    @Override public int addRows(Collection<Value[]> rows) {
        if (rows.isEmpty())
            return size;

        Data buff = createDataBuffer(rowSize(rows));

        for (Value[] row : rows)
            addRowToBuffer(row, buff);

        writeBufferToFile(buff);

        return size += rows.size();
    }

    /** {@inheritDoc} */
    @Override public int removeRow(Value[] values) {
        throw new UnsupportedOperationException(); // Supported only by sorted result.
    }

    /** {@inheritDoc} */
    @Override public boolean contains(Value[] values) {
        throw new UnsupportedOperationException(); // Supported only by sorted result.
    }

    /** {@inheritDoc} */
    @Override public synchronized ResultExternal createShallowCopy() {
        onChildCreated();

        return new PlainExternalResult(this);
    }

    @Override public ValueRow getRowKey(Object[] row) {
        throw new UnsupportedOperationException(); // Supported only by sorted result.
    }
}
