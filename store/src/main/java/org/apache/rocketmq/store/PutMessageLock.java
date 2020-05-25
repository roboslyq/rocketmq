/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.store;

/**
 * Used when trying to put message
 * 自旋锁，减少上下文切换：
 * 1、RocketMQ 的 CommitLog 为了避免并发写入，使用一个 PutMessageLock。
 * 2、PutMessageLock 有 2 个实现版本：PutMessageReentrantLock 和 PutMessageSpinLock。
 * 3、PutMessageReentrantLock 是基于 java 的同步等待唤醒机制；
 * 3、PutMessageSpinLock 使用 Java 的 CAS 原语，通过自旋设值实现上锁和解锁。
 * RocketMQ 默认使用 PutMessageSpinLock 以提高高并发写入时候的上锁解锁效率，并减少线程上下文切换次数。
 */
public interface PutMessageLock {
    void lock();

    void unlock();
}
