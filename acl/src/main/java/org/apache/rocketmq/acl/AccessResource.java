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

package org.apache.rocketmq.acl;

/**
 * 1、RocketMQ4.4.0加入的功能，在4.4之前内部没有安全相关的业务模块，
 * 2、消息的发送和消费得不到很好的安全管控需要业务方自己去封装安全模块，无形中增加了使用成本。
 * 3、访问控制，最基本会涉及：用户、资源、权限、角色四种抽象
 * (1)用户：用户是访问控制的基础要素，RocketMQ ACL必然也会引入用户的概念，即支持用户名、密码。
 * (2)资源：需要保护的对象，消息发送涉及的Topic、消息消费涉及的消费组，应该进行保护，故可以抽象成资源。
 * (3)权限：针对资源，能进行的操作。
 * (4)角色：RocketMQ中，只定义两种角色：是否是管理员。
 */
public interface AccessResource {
}
