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

package org.apache.ignite.internal.processors.cache.persistence.wal.reader;

import java.io.Serializable;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.spi.IgniteSpiAdapter;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.IgniteSpiNoop;
import org.apache.ignite.spi.communication.CommunicationListener;
import org.apache.ignite.spi.communication.CommunicationSpi;
import org.jetbrains.annotations.Nullable;

/**
 * No-operation SPI for standalone WAL reader
 */
@IgniteSpiNoop
public class StandaloneNoopCommunicationSpi extends IgniteSpiAdapter implements CommunicationSpi {
    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String igniteInstanceName) throws IgniteSpiException {

    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws IgniteSpiException {

    }

    /** {@inheritDoc} */
    @Override public void sendMessage(ClusterNode destNode, Serializable msg) throws IgniteSpiException {

    }

    /** {@inheritDoc} */
    @Override public int getSentMessagesCount() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public long getSentBytesCount() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public int getReceivedMessagesCount() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public long getReceivedBytesCount() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public int getOutboundMessagesQueueSize() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public void resetMetrics() {

    }

    /** {@inheritDoc} */
    @Override public void setListener(@Nullable CommunicationListener lsnr) {

    }
}
