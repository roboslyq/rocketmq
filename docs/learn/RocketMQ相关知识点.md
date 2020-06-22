

# 如何解决顺序消息？

> 顺序消息只能保证局部顺序，并且大多数业务场景也仅仅需要局部顺序。

**世界上解决一个计算机问题最简单的方法：“恰好”不需要解决它！——沈询**

因此，对于RocketMQ来说，MQ本身(Broker)不提供顺序消息的保证,而是通过生产者和消费者的控制，实现顺序消息。



# 如何解决重复消息？

> **世界上解决一个计算机问题最简单的方法：“恰好”不需要解决它！——沈询**

还是这个理论，RocketMQ本身不提供重复消息处理，而是将此任务交给了消费端。

1. 消费端处理消息的业务逻辑保持幂等性
2. 保证每条消息都有唯一编号且保证消息处理成功与去重表的日志同时出现

# 为何不用ZooKeeper？

ZooKeeper的功能很强大，包括自动Master选举等，RocketMQ的架构设计决定了它不需要进行Master选举， 用不到这些复杂的功能，只需要一个轻量级的元数据服务器就足够了。值得注意的是，NameServer并没有提供类似Zookeeper的watcher机制， 而是采用了每30s心跳机制。

# DMA

​		DMA(Direct Memory Access,直接存储器访问)。在DMA出现之前，CPU与外设之间的数据传送方式有**程序传送方式**、**中断传送方式**。CPU是通过系统总线与其他部件连接并进行数据传输。



> 参考资料：https://blog.csdn.net/zhejfl/article/details/82555634

# MMAP

mmap(memory map)是一种内存映射文件的方法，即将一个文件或者其它对象映射到进程的地址空间，实现文件磁盘地址和进程虚拟地址空间中一段虚拟地址的一一对映关系。实现这样的映射关系后，进程就可以采用指针的方式读写操作这一段内存，而系统会自动回写脏页面到对应的文件磁盘上，即完成了对文件的操作而不必再调用read,write等系统调用函数。相反，内核空间对这段区域的修改也直接反映用户空间，从而可以实现不同进程间的文件共享。如下图所示：

![img](https://images0.cnblogs.com/blog2015/571793/201507/200501092691998.png)

# ByteBuffer之DirectoryBuffer

# ByteBuffer之MappedByteBuffer

# RocketMQ集群高可用

> 参考资料https://www.dazhuanlan.com/2020/03/28/5e7ee76295f5e/

- #### Master Broker是如何将消息同步给Slave Broker的？

  - 为了保证MQ的数据不丢失而且具备一定的高可用性，所以一般都是得将Broker部署成Master-Slave模式的，也就是一个Master Broker对应一个Slave Broker。然后Master需要在接收到消息之后，将数据同步给Slave，这样一旦Master Broker挂了，还有Slave上有一份数据。
  - RocketMQ的Master-Slave模式采取的是Slave Broker不停的发送请求到Master Broker去**拉取**消息。
    ![avatar](https://s2.ax1x.com/2019/11/13/MYnnoV.png)

- #### RocketMQ 实现读写分离了吗？

  - 有可能从Master Broker获取消息，也有可能从Slave Broker获取消息。
  - 作为消费者的系统在获取消息的时候会先发送请求到Master Broker上去，请求获取一批消息，此时Master Broker是会返回一批消息给消费者系统的。然后Master Broker在返回消息给消费者系统的时候，会根据当时Master Broker的**负载情况**和Slave Broker的同步情况，向消费者系统**建议**下一次拉取消息的时候是从Master Broker拉取还是从Slave Broker拉取。

- #### 如果Slave Broke挂掉了有什么影响？

  - 因为消息写入全部是发送到Master Broker的，然后消息获取也可以走Master Broker，只不过有一些消息获取可能是从Slave Broker去走的。所以如果Slave Broker挂了，那么此时无论消息写入还是消息拉取，还是可以继续从Master Broke去走，对整体运行不影响。

- #### 如果Master Broker挂掉了该怎么办？

  - **RocketMQ 4.5版本之前**，都是用Slave Broker同步数据，尽量保证数据不丢失，但是一旦Master故障了，Slave是没法自动切换成Master的。
  - 如果Master Broker宕机了，这时就得**手动**做一些运维操作，把Slave Broker重新修改一些配置，重启机器给调整为Master Broker。
  - Master-Slave模式不是彻底的高可用模式，他没法实现自动把Slave切换为Master

- #### 基于Dledger实现RocketMQ高可用自动切换（RocketMQ 4.5之后）

  - 把Dledger融入RocketMQ之后，就可以让一个Master Broker对应多个Slave Broker，也就是说一份数据可以有多份副本，比如一个Master Broker对应两个Slave Broker。
  - Master Broker宕机了，就可以在多个副本，也就是多个Slave中，通过**Dledger技术和Raft协议算法**进行leader选举，直接将一个Slave Broker选举为新的Master Broker，然后这个新的Master Broker就可以对外提供服务了。