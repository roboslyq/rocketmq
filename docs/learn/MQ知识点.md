# DLedger

一款基于Raft协议的Commitlog 存储 Library

> https://github.com/openmessaging/openmessaging-storage-dledger

类似的产品有 [Ratis](https://github.com/apache/incubator-ratis)。

​		在 Apache RocketMQ 中，DLedger 不仅被直接用来当做**消息存储**，也被用来实现一个嵌入式的 **KV 系统**，以存储元数据信息。

# CommitLog

提交日志，应该对应一个文件

 commtLog的存储其实是分多层的，commitLog -> mappedFileQueue -> mappedFile，其中真正存储数据的是mappedFile。

  commitLog在mappedFile当中存储消息的格式是【msg + msg + msg + ......+msg+blank】。也就是当最后的位置放不下消息的时候就填充空白。

# NameServer

NameServers本身是状态的，相互之间独立，因此可以很方便的部署多个。其他角色同时向多个NameServer机器上报状态信息，从而达到热备份的目的。 NameServer本身是无状态的，也就是说NameServer中的Broker、Topic等状态信息不会持久存储，都是由各个角色定时上报并 存储到内存中的(NameServer支持配置参数的持久化，一般用不到)

# 为何不用ZooKeeper？

ZooKeeper的功能很强大，包括自动Master选举等，RocketMQ的架构设计决定了它不需要进行Master选举， 用不到这些复杂的功能，只需要一个轻量级的元数据服务器就足够了。值得注意的是，NameServer并没有提供类似Zookeeper的watcher机制， 而是采用了每30s心跳机制。
