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
package org.apache.rocketmq.acl.common;

/**
 * ACL常量类，定义了常见配置
 */
public class AclConstants {
    // 类型：数组，格式：192.168.0.{100,101}或 192.168.1.100,192.168.2.100或者 * 或者192.168.*.或192.168.100-200.10-20。支持简单表达式
    public static final String CONFIG_GLOBAL_WHITE_ADDRS = "globalWhiteRemoteAddresses";
    //类型：集合，格式: 账号集合，包含accessKey、secretKey、whiteRemoteAddress、admin、defaultTopicPerm、defaultGroupPerm、topicPerms、groupPerms
    public static final String CONFIG_ACCOUNTS = "accounts";
    //账户名称，长度必须大于6个字符。
    public static final String CONFIG_ACCESS_KEY = "accessKey";
    //密码，长度必须大于6个字符。
    public static final String CONFIG_SECRET_KEY = "secretKey";
    // 用户级别的IP地址白名单：规则与全局白名单相似，但只能有一条
    public static final String CONFIG_WHITE_ADDR = "whiteRemoteAddress";
    /**
     * boolean类型，设置是否是admin。如下权限只有admin=true时才有权限执行。
     * UPDATE_AND_CREATE_TOPIC：    更新或创建主题。
     * UPDATE_BROKER_CONFIG：       更新Broker配置。
     * DELETE_TOPIC_IN_BROKER：     删除主题。
     * UPDATE_AND_CREATE_SUBSCRIPTIONGROUP：   更新或创建订阅组信息
     * DELETE_SUBSCRIPTIONGROUP：     删除订阅组信息。
     */
    public static final String CONFIG_ADMIN_ROLE = "admin";
    /**
     * 默认topic权限。该值默认为DENY(拒绝)。
     */
    public static final String CONFIG_DEFAULT_TOPIC_PERM = "defaultTopicPerm";
    /**
     * 默认消费者组权限，该值默认为DENY(拒绝)，建议值为SUB。
     */
    public static final String CONFIG_DEFAULT_GROUP_PERM = "defaultGroupPerm";
    /**
     * 设置topic的权限。其类型为数组
     */
    public static final String CONFIG_TOPIC_PERMS = "topicPerms";
    /**
     * 设置消费组的权限。其类型为数组，
     */
    public static final String CONFIG_GROUP_PERMS = "groupPerms";

    public static final String CONFIG_DATA_VERSION = "dataVersion";

    public static final String CONFIG_COUNTER = "counter";

    public static final String CONFIG_TIME_STAMP = "timestamp";

    public static final int ACCESS_KEY_MIN_LENGTH = 6;

    public static final int SECRET_KEY_MIN_LENGTH = 6;
}
