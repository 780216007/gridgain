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

#include "ignite/impl/cache/cache_affinity_impl.h"

using namespace ignite::common;
using namespace ignite::common::concurrent;
using namespace ignite::cluster;
using namespace ignite::jni::java;
using namespace ignite::impl::cluster;

namespace ignite
{
    namespace impl
    {
        namespace cache
        {
            CacheAffinityImpl::CacheAffinityImpl(SP_IgniteEnvironment env, jobject javaRef)
                : InteropTarget(env, javaRef)
            {

            }

            int CacheAffinityImpl::GetPartitions()
            {
                IgniteError err;

                int ret = static_cast<int>(OutInOpLong(Command::PARTITIONS, 0, err));

                IgniteError::ThrowIfNeeded(err);

                return ret;
            }

            std::vector<int> CacheAffinityImpl::GetPrimaryPartitions(ignite::cluster::ClusterNode node)
            {
                return GetPartitions(Command::PRIMARY_PARTITIONS, node);
            }

            std::vector<int> CacheAffinityImpl::GetBackupPartitions(ignite::cluster::ClusterNode node)
            {
                return GetPartitions(Command::BACKUP_PARTITIONS, node);
            }

            std::vector<int> CacheAffinityImpl::GetAllPartitions(ignite::cluster::ClusterNode node)
            {
                return GetPartitions(Command::ALL_PARTITIONS, node);
            }

            ignite::cluster::ClusterNode CacheAffinityImpl::MapPartitionToNode(int part)
            {
                Guid nodeId;
                In1Operation<int> inOp(part);
                Out1Operation<Guid> outOp(nodeId);

                IgniteError err;
                InteropTarget::OutInOp(Command::MAP_PARTITION_TO_NODE, inOp, outOp, err);
                IgniteError::ThrowIfNeeded(err);

                return GetEnvironment().GetNode(nodeId);
            }

            std::map<int, ignite::cluster::ClusterNode> CacheAffinityImpl::MapPartitionsToNodes(std::vector<int> parts)
            {
                SharedPointer<interop::InteropMemory> memIn = GetEnvironment().AllocateMemory();
                SharedPointer<interop::InteropMemory> memOut = GetEnvironment().AllocateMemory();
                interop::InteropOutputStream out(memIn.Get());
                binary::BinaryWriterImpl writer(&out, GetEnvironment().GetTypeManager());

                writer.WriteInt32(static_cast<int32_t>(parts.size()));
                for (size_t i = 0; i < parts.size(); i++)
                    writer.WriteObject<int>(parts.at(i));

                out.Synchronize();

                IgniteError err;
                InStreamOutStream(Command::MAP_PARTITIONS_TO_NODES, *memIn.Get(), *memOut.Get(), err);
                IgniteError::ThrowIfNeeded(err);

                interop::InteropInputStream inStream(memOut.Get());
                binary::BinaryReaderImpl reader(&inStream);

                std::map<int, ClusterNode> ret;

                int32_t cnt = reader.ReadInt32();
                for (int32_t i = 0; i < cnt; i++)
                {
                    int key = reader.ReadInt32();
                    ClusterNode val(GetEnvironment().GetNode(reader.ReadGuid()));

                    ret.insert(std::pair<int, ClusterNode>(key, val));
                }

                return ret;
            }

            std::list<ClusterNode> CacheAffinityImpl::MapPartitionToPrimaryAndBackups(int part)
            {
                SharedPointer<interop::InteropMemory> memIn = GetEnvironment().AllocateMemory();
                SharedPointer<interop::InteropMemory> memOut = GetEnvironment().AllocateMemory();
                interop::InteropOutputStream out(memIn.Get());
                binary::BinaryWriterImpl writer(&out, GetEnvironment().GetTypeManager());

                writer.WriteObject(part);

                out.Synchronize();

                IgniteError err;
                InStreamOutStream(Command::MAP_PARTITION_TO_PRIMARY_AND_BACKUPS, *memIn.Get(), *memOut.Get(), err);
                IgniteError::ThrowIfNeeded(err);

                interop::InteropInputStream inStream(memOut.Get());
                binary::BinaryReaderImpl reader(&inStream);

                std::list<ClusterNode> ret;
                int32_t cnt = reader.ReadInt32();
                for (int32_t i = 0; i < cnt; i++)
                    ret.push_back(GetEnvironment().GetNode(reader.ReadGuid()));

                return ret;
            }

            std::vector<int> CacheAffinityImpl::GetPartitions(int32_t opType, ClusterNode node)
            {
                SharedPointer<interop::InteropMemory> memIn = GetEnvironment().AllocateMemory();
                SharedPointer<interop::InteropMemory> memOut = GetEnvironment().AllocateMemory();
                interop::InteropOutputStream out(memIn.Get());
                binary::BinaryWriterImpl writer(&out, GetEnvironment().GetTypeManager());

                writer.WriteGuid(node.GetId());

                out.Synchronize();

                IgniteError err;
                InStreamOutStream(opType, *memIn.Get(), *memOut.Get(), err);
                IgniteError::ThrowIfNeeded(err);

                interop::InteropInputStream inStream(memOut.Get());
                binary::BinaryReaderImpl reader(&inStream);

                std::vector<int> ret;

                reader.ReadInt8();
                int32_t cnt = reader.ReadInt32();
                for (int32_t i = 0; i < cnt; i++)
                    ret.push_back(reader.ReadInt32());

                return ret;
            }
        }
    }
}