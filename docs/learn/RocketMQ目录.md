# RocketMQ目录 

# 1.基本概念

## 应用场景

## 概念

# 2.环境安装

## 源码环境

## Windows安装

## Linux安装

# 3.NameSrv

NameServers本身是状态的，相互之间独立，因此可以很方便的部署多个。其他角色同时向多个NameServer机器上报状态信息，从而达到热备份的目的。 NameServer本身是无状态的，也就是说NameServer中的Broker、Topic等状态信息不会持久存储，都是由各个角色定时上报并 存储到内存中的(NameServer支持配置参数的持久化，一般用不到)

# 4.Producer

# 5.Consumer

# 6.Brokder之普通消息

## 6.1.消息接收

## 6.2消息存储

### CommitLog

提交日志，应该对应一个文件

 commtLog的存储其实是分多层的，commitLog -> mappedFileQueue -> mappedFile，其中真正存储数据的是mappedFile。

  commitLog在mappedFile当中存储消息的格式是【msg + msg + msg + ......+msg+blank】。也就是当最后的位置放不下消息的时候就填充空白。

# 7.Brokder之顺序消息

# 8.Broker之事务消息

# 9. Broker之Cluster

## Cluster环境搭建

## DLedger

背景:目前分布式系统做到高可靠和高可用有Master/Slave，Based on ZooKeeper/Etcd 和 Raft这三种方法。做到高可靠和高可用的基本的实现方法，各有优劣。

- Master/Slave

  优点：实现简单

  缺点：不能自动控制节点切换，一旦出了问题，需要人为介入。

- Based on Zookeeper/Etcd

  优点：可以自动切换节点

  缺点：运维成本很高，因为 ZooKeeper 本身就很难运维。

- Raft

  优点：可以自己协调，并且去除依赖。

  缺点：实现 Raft，在编码上比较困难。

  Raft 算法现在解析的比较多，也比较成熟，代码实现难度也有所降低。Dledger 作为一个轻量级的 Java Library，它的作用就是将 Raft 有关于算法方面的内容全部抽象掉，开发人员只需要关心业务即可。

### Dledger

主从复制，多副本实现。一款基于Raft协议的Commitlog 存储 Library。Dledger 作为一个轻量级的 Java Library，它的作用就是将 Raft 有关于算法方面的内容全部抽象掉，开发人员只需要关心业务即可。

> https://github.com/openmessaging/openmessaging-storage-dledger

类似的产品有 [Ratis](https://github.com/apache/incubator-ratis)。

​		在 Apache RocketMQ 中，DLedger 不仅被直接用来当做**消息存储**，也被用来实现一个嵌入式的 **KV 系统**，以存储元数据信息。




