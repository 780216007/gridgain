﻿/*
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

namespace Apache.Ignite.Core.Tests.Cache.Near
{
    using System;
    using System.Collections.Generic;
    using System.Linq;
    using System.Threading;
    using System.Threading.Tasks;
    using Apache.Ignite.Core.Cache;
    using Apache.Ignite.Core.Cache.Configuration;
    using Apache.Ignite.Core.Cache.Eviction;
    using Apache.Ignite.Core.Cache.Query;
    using Apache.Ignite.Core.Events;
    using NUnit.Framework;

    /// <summary>
    /// Near cache test.
    /// </summary>
    public class CacheNearTest
    {
        /** */
        protected const string CacheName = "default";

        /** */
        private const int NearCacheMaxSize = 3;

        /** */
        private IIgnite _grid;

        /** */
        private IIgnite _grid2;

        /** */
        private IIgnite _client;

        /// <summary>
        /// Fixture set up.
        /// </summary>
        [TestFixtureSetUp]
        public virtual void FixtureSetUp()
        {
            var cfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                CacheConfiguration = new[]
                {
                    new CacheConfiguration
                    {
                        NearConfiguration = new NearCacheConfiguration
                        {
                            EvictionPolicy = new FifoEvictionPolicy {MaxSize = NearCacheMaxSize}
                        },
                        Name = CacheName,
                        QueryEntities = new[]
                        {
                            new QueryEntity(typeof(Foo))
                        }
                    }
                },
                IgniteInstanceName = "server1"
            };

            _grid = Ignition.Start(cfg);
            
            var cfg2 = new IgniteConfiguration(cfg)
            {
                IgniteInstanceName = "server2"
            };

            _grid2 = Ignition.Start(cfg2);

            var clientCfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                ClientMode = true,
                IgniteInstanceName = "client",
                IncludedEventTypes = new[] {EventType.CacheEntryCreated}
            };

            _client = Ignition.Start(clientCfg);
            
            WaitForRebalance();
        }

        /// <summary>
        /// Fixture tear down.
        /// </summary>
        [TestFixtureTearDown]
        public void FixtureTearDown()
        {
            Ignition.StopAll(true);
        }

        /// <summary>
        /// Test tear down.
        /// </summary>
        [TearDown]
        public void TearDown()
        {
            _grid.GetCache<int, int>(CacheName).RemoveAll();
        }

        /// <summary>
        /// Tests the existing near cache.
        /// </summary>
        [Test]
        public void TestExistingNearCache()
        {
            var cache = _grid.GetCache<int, int>(CacheName);
            cache[1] = 1;

            var nearCache = _grid.GetOrCreateNearCache<int, int>(CacheName, new NearCacheConfiguration());
            Assert.AreEqual(1, nearCache[1]);

            // GetOrCreate when exists
            nearCache = _grid.GetOrCreateNearCache<int, int>(CacheName, new NearCacheConfiguration());
            Assert.AreEqual(1, nearCache[1]);

            cache[1] = 2;
            Assert.AreEqual(2, nearCache[1]);
        }

        /// <summary>
        /// Tests that near cache does not return same instance that we Put there:
        /// there is always serialize-deserialize roundtrip.
        /// </summary>
        [Test]
        public void TestNearCachePutGetReturnsNewObject(
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode)
        {
            var cache = GetCache<int, Foo>(mode);
            var key = (int) mode;

            var obj = new Foo(key);
            
            cache[key] = obj;
            var res1 = cache[key];
            var res2 = cache[key];

            // Returned object is Equal to the initial.
            Assert.AreEqual(obj, res1);
            
            // But not the same - new instance is stored in Near Cache.
            Assert.AreNotSame(obj, res1);
            
            // Repeated Get call returns same instance from Near Cache.
            Assert.AreSame(res1, res2);
        }

        /// <summary>
        /// Tests that near cache returns the same object on every get.
        /// </summary>
        [Test]
        public void TestNearCacheRepeatedGetReturnsSameObjectReference(
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode,
            [Values(true, false)] bool primaryKey,
            [Values(true, false)] bool localPut)
        {
            var cache = GetCache<int, Foo>(mode);
            var key = TestUtils.GetKey(_grid, cache.Name, primaryKey: primaryKey);

            var obj = new Foo(3);

            if (localPut)
            {
                // Local put through the same cache instance: obj is in .NET Near Cache directly.
                cache[key] = obj;
            }
            else
            {
                // Put through remote node: near cache is updated only on Get.
                var remoteCache = GetCache<int, Foo>(
                    mode == CacheTestMode.Client ? CacheTestMode.ServerRemote : CacheTestMode.Client);
                
                remoteCache[key] = obj;
            }
            
            var res1 = cache[key];
            var res2 = cache[key];
            
            Assert.AreSame(res1, res2);
            Assert.AreEqual(3, res1.Bar);
        }
        
        /// <summary>
        /// Tests that near cache returns the same object on every get.
        /// </summary>
        [Test]
        public void TestNearCacheRepeatedRemoteGetReturnsSameObjectReference(
            [Values(CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode,
            [Values(true, false)] bool primaryKey)
        {
            var remoteCache = GetCache<int, Foo>(CacheTestMode.ServerLocal);
            var localCache = GetCache<int, Foo>(mode);
            var key = TestUtils.GetKey(_grid, remoteCache.Name, primaryKey: primaryKey);

            remoteCache[key] = new Foo();

            Assert.IsTrue(TestUtils.WaitForCondition(() =>
            {
                Foo val;

                return localCache.TryGet(key, out val) && 
                       ReferenceEquals(val, localCache.Get(key));
            }, 300));
            
            // Invalidate after get.
            remoteCache[key] = new Foo(1);
            
            Assert.IsTrue(TestUtils.WaitForCondition(() =>
            {
                Foo val;

                return localCache.TryGet(key, out val) &&
                       val.Bar == 1 &&
                       ReferenceEquals(val, localCache.Get(key));
            }, 300));
        }
        
        /// <summary>
        /// Tests that near cache is updated from remote node after being populated with local Put call.
        /// </summary>
        [Test]
        public void TestNearCacheUpdatesFromRemoteNode(
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode1,
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode2)
        {
            var cache1 = GetCache<int, int>(mode1);
            var cache2 = GetCache<int, int>(mode2);

            cache1[1] = 1;
            cache2[1] = 2;

            Assert.True(TestUtils.WaitForCondition(() => cache1[1] == 2, 300));
        }

        /// <summary>
        /// Tests that near cache is updated from another cache instance after being populated with local Put call.
        /// </summary>
        [Test]
        public void TestNearCacheUpdatesFromAnotherLocalInstance()
        {
            var cache1 = _grid.GetCache<int, int>(CacheName);
            var cache2 = _grid.GetCache<int, int>(CacheName);

            cache1[1] = 1;
            cache2.Replace(1, 2);

            Assert.True(TestUtils.WaitForCondition(() => cache1[1] == 2, 300));
        }

        /// <summary>
        /// Tests that near cache is cleared from remote node after being populated with local Put call.
        /// </summary>
        [Test]
        public void TestNearCacheRemoveFromRemoteNodeAfterLocalPut()
        {
            var localCache = _client.GetOrCreateNearCache<int, int>(CacheName, new NearCacheConfiguration());
            var remoteCache = _grid.GetCache<int, int>(CacheName);

            localCache[1] = 1;
            remoteCache.Remove(1);

            int unused;
            Assert.True(TestUtils.WaitForCondition(() => !localCache.TryGet(1, out unused), 300));
        }

        /// <summary>
        /// Tests that same near cache can be used with different sets of generic type parameters.
        /// </summary>
        [Test]
        public void TestSameNearCacheWithDifferentGenericTypeParameters()
        {
            var cache1 = _grid.GetCache<int, int>(CacheName);
            var cache2 = _grid.GetCache<string, string>(CacheName);
            var cache3 = _grid.GetCache<int, Foo>(CacheName);
            var cache4 = _grid.GetCache<object, object>(CacheName);

            cache1[1] = 1;
            cache2["1"] = "1";
            cache3[2] = new Foo(5);

            Assert.AreEqual(cache4[1], 1);
            Assert.AreEqual(cache4["1"], "1");
            Assert.AreSame(cache4[2], cache3[2]);
        }

        /// <summary>
        /// Tests that cache data is invalidated in the existing cache instance after generic downgrade.
        /// </summary>
        [Test]
        public void TestDataInvalidationAfterGenericDowngrade()
        {
            var cacheName = TestContext.CurrentContext.Test.Name;
            var cfg = new CacheConfiguration
            {
                Name = cacheName,
                NearConfiguration = new NearCacheConfiguration()
            };

            var cache = _client.CreateCache<int, int>(cfg, cfg.NearConfiguration);
            cache[1] = 1;

            var newCache = _client.GetOrCreateNearCache<int, object>(cacheName, cfg.NearConfiguration);
            newCache[1] = 2;

            Assert.AreEqual(2, cache[1]);
        }

        /// <summary>
        /// Tests that error during Put removes near cache value for that key.
        /// </summary>
        [Test]
        public void TestFailedPutRemovesNearCacheValue()
        {
            // TODO: use store to cause error during put
        }

        /// <summary>
        /// Tests that near cache is updated/invalidated by SQL DML operations.
        /// </summary>
        [Test]
        public void TestSqlUpdatesNearCache()
        {
            var cache = GetCache<int, Foo>(CacheTestMode.Client);

            var value = new Foo(5);
            cache[1] = value;
            
            cache.Query(new SqlFieldsQuery("update Foo set Bar = 7 where Bar = 5"));

            var res = cache[1];
            Assert.AreEqual(7, res.Bar);
        }

        /// <summary>
        /// Tests that eviction policy removes near cache data for the key. 
        /// </summary>
        [Test]
        public void TestFifoEvictionPolicyRemovesNearCacheValue(
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode)
        {
            var cache = GetCache<int, Foo>(mode);
            
            TestEvictionPolicyRemovesNearCacheValue(mode, cache);
        }

        /// <summary>
        /// Tests that eviction policy removes near cache data for the key. 
        /// </summary>
        [Test]
        public void TestLruEvictionPolicyRemovesNearCacheValue(
            [Values(CacheTestMode.ServerLocal, CacheTestMode.ServerRemote, CacheTestMode.Client)] CacheTestMode mode)
        {
            var cfg = new CacheConfiguration
            {
                Name = "lru-test-" + mode,
                NearConfiguration = new NearCacheConfiguration
                {
                    EvictionPolicy = new LruEvictionPolicy
                    {
                        MaxSize = NearCacheMaxSize,
                        BatchSize = 1
                    }
                }
            };

            var ignite = GetIgnite(mode);
            var cache = ignite.CreateCache<int, Foo>(cfg, cfg.NearConfiguration);
            
            TestEvictionPolicyRemovesNearCacheValue(mode, cache);
        }

        /// <summary>
        /// Tests that evicted entry is reloaded from Java after update from another node.
        /// Eviction on Java side for non-local entry (not a primary key for this node) disconnects near cache notifier.
        /// This test verifies that eviction on Java side causes eviction on .NET side, and does not cause stale data.
        /// </summary>
        [Test]
        public void TestCacheGetFromEvictedEntryAfterUpdateFromAnotherNode()
        {
            // TODO: Add other modes, especially:
            // * Local keys on server nodes
            // * Non-local keys on server nodes
            var cfg = new CacheConfiguration
            {
                Name = TestContext.CurrentContext.Test.Name,
                NearConfiguration = new NearCacheConfiguration
                {
                    EvictionPolicy = new FifoEvictionPolicy
                    {
                        MaxSize = 1
                    }
                }
            };

            var serverCache = _grid.CreateCache<int, int>(cfg);
            var clientCache = _client.GetOrCreateNearCache<int, int>(cfg.Name, cfg.NearConfiguration);
            
            clientCache[1] = 1;
            clientCache[2] = 2;
            serverCache[1] = 11;
            
            Assert.AreEqual(11, clientCache[1]);
        }

        [Test]
        public void TestScanQueryUsesValueFromNearCache()
        {
            // TODO: Can we pass only the key to scan query filter, and pass value only when needed?
            //       - yes, should be easy within callback (.NET side "replies" into the same stream with a flag)
            // TODO: When receiving scan query results, can we also use existing values from near cache?
            //      - yes we can, by writing keys for the batch first, then retrieving missing values as a separate call
        }

        [Test]
        public void TestExpiryPolicyRemovesValuesFromNearCache()
        {
            // TODO: WithExpiryPolicy
            // TODO: CacheConfiguration.ExpiryPolicy
        }

        [Test]
        public void TestMultithreadedConcurrentUpdates()
        {
            // TODO: Test concurrent updates from same node or different nodes
            // Separate reader thread should check happens-after semantics
            var localCache = GetCache<int, Foo>(CacheTestMode.Client);
            var remoteCache = GetCache<int, Foo>(CacheTestMode.ServerRemote);
            var cancel = false;
            const int key = 1;
            var id = 1;
            var getCount = 0;
            remoteCache[1] = new Foo(id);

            var localUpdater = Task.Factory.StartNew(() =>
            {
                while (!cancel)
                {
                    Interlocked.Increment(ref id);
                    localCache.Put(key, new Foo(id));
                }
            });

            var remoteUpdater = Task.Factory.StartNew(() =>
            {
                while (!cancel)
                {
                    Interlocked.Increment(ref id);
                    remoteCache.Put(key, new Foo(id));
                }
            });
            
            var localReader = Task.Factory.StartNew(() =>
            {
                while (!cancel)
                {
                    var cur = localCache[key].Bar;
                    Assert.GreaterOrEqual(id, cur);
                    getCount++;
                }
            });

            Thread.Sleep(3000);
            cancel = true;
            Task.WaitAll(localUpdater, remoteUpdater, localReader);

            // Get actual value with SQL to bypass caches and verify.
            var actualValue = (int) localCache.Query(new SqlFieldsQuery("select Bar from Foo")).GetAll()[0][0];
            Assert.AreEqual(id, actualValue);
            Assert.AreEqual(id, localCache[key].Bar, "Local value");
            Assert.AreEqual(id, remoteCache[key].Bar, "Remote value");
            Console.WriteLine("Writes: {0}, Reads: {1}", id, getCount);
            Console.WriteLine("Actual value: " + actualValue);
        }

        [Test]
        public void TestNearCacheAllOperations()
        {
            // TODO: Can we split this test?
            // Write ops:
            // - check reference equality locally
            // - check update remotely
            // Read ops:
            // - check reference equality after multiple calls
            // - check update from local (reference equality)
            // - check update from remote
        }
        
        /// <summary>
        /// Gets the cache instance.
        /// </summary>
        private ICache<TK, TV> GetCache<TK, TV>(CacheTestMode mode, string name = CacheName)
        {
            var nearConfiguration = _grid.GetCache<TK, TV>(name).GetConfiguration().NearConfiguration;
            
            // For server nodes we could just say GetCache - near is created automatically.
            return GetIgnite(mode).GetOrCreateNearCache<TK, TV>(name, nearConfiguration);
        }

        /// <summary>
        /// Gets Ignite instance for mode.
        /// </summary>
        private IIgnite GetIgnite(CacheTestMode mode)
        {
            return new[] {_grid, _grid2, _client}[(int) mode];
        }

        /// <summary>
        /// Tests that eviction policy removes near cache data for the key. 
        /// </summary>
        private void TestEvictionPolicyRemovesNearCacheValue(CacheTestMode mode, ICache<int, Foo> cache)
        {
            // Use non-primary keys: primary keys are not evicted.
            var items = TestUtils
                .GetKeys(GetIgnite(mode), cache.Name, primary: false)
                .Take(NearCacheMaxSize + 1)
                .Select(x => new Foo(x))
                .ToArray();

            var cachedItems = new List<Foo>();

            foreach (var item in items)
            {
                cache[item.Bar] = item;
                cachedItems.Add(cache[item.Bar]);
            }

            // Recent items are in near cache:
            foreach (var item in cachedItems.Skip(items.Length - NearCacheMaxSize))
            {
                Assert.AreSame(item, cache[item.Bar]);
            }

            // First item is deserialized on get:
            var localItem = items[0];
            var key = localItem.Bar;

            var fromCache = cache[key];
            Assert.AreNotSame(localItem, fromCache);

            // And now it is near again:
            Assert.AreSame(fromCache, cache[key]);
        }

        private void WaitForRebalance()
        {
            Assert.IsTrue(
                TestUtils.WaitForCondition(
                    () => _grid2.GetAffinity(CacheName).MapKeyToNode(1).IsLocal, 2000));
        }

        /** */
        private class Foo : IEquatable<Foo>
        {
            public Foo(int bar = 0)
            {
                Bar = bar;
            }

            [QuerySqlField]
            public readonly int Bar;

            [QuerySqlField]
            public readonly string TestName = TestContext.CurrentContext.Test.Name;

            public override string ToString()
            {
                return string.Format("Foo [Bar={0}, TestName={1}]", Bar, TestName);
            }

            public bool Equals(Foo other)
            {
                return Bar == other.Bar && TestName == other.TestName;
            }

            public override bool Equals(object obj)
            {
                if (ReferenceEquals(null, obj)) return false;
                if (ReferenceEquals(this, obj)) return true;
                if (obj.GetType() != this.GetType()) return false;
                return Equals((Foo) obj);
            }

            public override int GetHashCode()
            {
                unchecked
                {
                    return (Bar * 397) ^ (TestName != null ? TestName.GetHashCode() : 0);
                }
            }
        }
        
        /** */
        public enum CacheTestMode
        {
            ServerLocal,
            ServerRemote,
            Client
        }
    }
}
