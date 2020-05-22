# RocketMQ调试环境搭建

## 前提

> JDK 1.8或者之上
>
> IDEA
>
> Maven 3+
>
> Git

## Fork

> 为了学习源码方便自己做笔记，我们需要自己的一个仓库，因此我们第一件要做的事情就是从官网github fork一份代码到自己的空间。RocketMQ git官网地址; https://github.com/apache/rocketmq 

## Clone

> git clone https://github.com/roboslyq/rocketmq.git

## IDEA

将pom.xml直接拖拽到DIEA中，即可以打开（当然其它方式也行）。等待maven自动下载依赖包。

![1](./images/01/1.jpg)

## 启动namesrv

### 配置环境变量

> 配置ROCKETMQ_HOME,当然可以直接配置在计算的环境变量里,我这里直接配置在IDEA中。注意，当前版本必须指定到源码包解压后的`distribution`这个目录 ，因为这个目录有一个`conf`文件夹，保存了启动需要的默认配置。

`ROCKETMQ_HOME=E:\workspace\github\rocketmq\distribution`

![2](./images/01/2.jpg)

### 启动

> rocketmq\namesrv\src\main\java\org\apache\rocketmq\namesrv\NamesrvStartup.java

![3](./images/01/3.jpg)

## 启动broker

### 配置环境变量

> 与`namesrv`配置一致,但多了一个Program arguments参数: `-n 127.0.0.1:9786`。指定了`namesrv`服务器的地址和端口。

`-n 127.0.0.1:9876`

`ROCKETMQ_HOME=E:\workspace\github\rocketmq\distribution`

![4](./images/01/4.jpg)

### 启动

![5](./images/01/5.jpg)



## 可视化管理端

> 还是一样，先fork到自己的空间： https://github.com/apache/rocketmq-externals 。然后`git clone`，再然后用IDEA打开`rocketmq-console`这个模块。

![6](./images/01/6.jpg)

### 修改配置

> application.properties

指定`namesvr`服务的端口和地址

```properties
rocketmq.config.namesrvAddr=localhost:9876
```

### 启动

> rocketmq-externals\rocketmq-console\src\main\java\org\apache\rocketmq\console\App.java

![7](./images/01/7.jpg)

### 浏览器访问

![8](./images/01/8.jpg)

### 新建Topic

![9](./images/01/9.jpg)

![10](./images/01/10.jpg)

## 测试

### Maven配置

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.3.0</version>
</dependency>
```



### 生产者

```java
  public static void syncProducer() throws Exception {
        // 实例化消息生产者Producer
        DefaultMQProducer producer = new DefaultMQProducer("group_roboslyq_test1");
        // 设置NameServer的地址
        producer.setNamesrvAddr("localhost:9876");
        // 启动Producer实例
        producer.start();
        for (int i = 0; i < 100; i++) {
            // 创建消息，并指定Topic，Tag和消息体
            Message msg = new Message("group_roboslyq_test1" /* Topic */,
                    "TagA" /* Tag */,
                    ("Hello RocketMQ " + i).getBytes(RemotingHelper.DEFAULT_CHARSET) /* Message body */
            );
            // 发送消息到一个Broker
            SendResult sendResult = producer.send(msg);
            // 通过sendResult返回消息是否成功送达
            System.out.printf("%s%n", sendResult);
        }
        // 如果不再发送消息，关闭Producer实例。
        producer.shutdown();
    }
```

启动后日志:

![11](./images/01/11.jpg)

### 消费者

```java

    public static void consumer() throws MQClientException {
        // 实例化消费者
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("group_roboslyq_test1");

        // 设置NameServer的地址
        consumer.setNamesrvAddr("localhost:9876");

        // 订阅一个或者多个Topic，以及Tag来过滤需要消费的消息
        consumer.subscribe("group_roboslyq_test1", "*");
        // 注册回调实现类来处理从broker拉取回来的消息
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
                // 标记该消息已经被成功消费
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        // 启动消费者实例
        consumer.start();
        System.out.printf("Consumer Started.%n");
    }
```

![12](./images/01/12.jpg)

### 控制台查询

![13](./images/01/13.jpg)





> 至此，整个运行环境基本搭建完成！！！

