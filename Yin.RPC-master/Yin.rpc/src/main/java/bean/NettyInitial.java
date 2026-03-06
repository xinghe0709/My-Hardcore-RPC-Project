package bean;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.IdleStateHandler;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import constants.Constans;
import factory.ZooKeeperFactory;
import handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

@Component
public class NettyInitial implements ApplicationListener<ContextRefreshedEvent> {
	
	public  void start() {		
		NioEventLoopGroup boss = new NioEventLoopGroup(); // 负责接待新连接
		NioEventLoopGroup work = new NioEventLoopGroup(); // 负责处理读写数据
			
		try {//启动辅助
			ServerBootstrap serverBootstrap = new ServerBootstrap();
			serverBootstrap.group(boss, work)
					//如果瞬间涌入了一大批客户端连服务器，大堂经理（boss）处理不过来怎么办？
					// 操作系统会把处理不过来的连接放在一个队列里排队。这个参数就是设置那个排队队列的长度为 128。
				   .option(ChannelOption.SO_BACKLOG, 128)//设置TCP队列大小:包含已连接+未连接
				   .option(ChannelOption.SO_KEEPALIVE, false)//不使用默认的心跳机制
				   .channel(NioServerSocketChannel.class) //告诉 Netty，我们要使用基于 NIO（非阻塞 IO）的模型来搭建服务器。

					//当 boss 接到新客人，并把客人交给 work 后，这个客人的数据流就会进入下面这个 initChannel 配置好的流水线（Pipeline）里依次挨个处理：
				   .childHandler(new ChannelInitializer<SocketChannel>() {

					@Override
					protected void initChannel(SocketChannel ch) throws Exception {

						// 设置\r\n为分隔符，防粘包工序。底层的 TCP 是像水流一样的字节流，它可不管你发的是一个 JSON 还是半个 JSON，它可能把两个请求粘在一起发过来。
						ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0]));
						ch.pipeline().addLast(new StringDecoder());//字符串解码器
						ch.pipeline().addLast(new IdleStateHandler(20, 15, 10, TimeUnit.SECONDS));

						//虽然此时还在 work 线程，但作者做了一个极其重要的决定——提交给 Executor exec（业务线程池）。
						//work 线程把这个 String 消息往线程池里一丢，它的 IO 任务就完成了，立刻回去处理下一个网络包。
						ch.pipeline().addLast(new ServerHandler());//业务逻辑处理处
						ch.pipeline().addLast(new StringEncoder());//字符串编码器
					}
				   });
	
			int port = 8080;

			//在 Netty 中，所有的 I/O 操作（如 connect, write, close）都是异步的。
			// 调用这些方法会立即返回，而 ChannelFuture 就是那张**“取货凭证”**，代表了一个尚未完成的操作结果。

			//无论在服务端还是客户端，CuratorFramework 的本质就是一个**“Zookeeper 官方指定的高级通讯手机”**。
			//Zookeeper（ZK）是一台远在天边的中央服务器（类似房产中介的数据库）。你的 Java 程序想和这台中介数据库说话，就必须得有一部能连上它的手机。
			// CuratorFramework 就是这部手机。

			//底层动作：Netty 的 Boss 线程池跑去操作系统那里，成功把 8080 端口绑定了。
			//此时的返回值 f，里面包裹着的，正是刚才建好的那扇**“迎宾大门” (NioServerSocketChannel)**。
			ChannelFuture f = serverBootstrap.bind(8080).sync();
		
			InetAddress address = InetAddress.getLocalHost();
			CuratorFramework client = ZooKeeperFactory.getClient();
			if(client != null){
				System.out.println(client);
				// 核心代码：在 ZK 上创建临时节点，告诉全世界“我上线了”
				client.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(Constans.SERVER_PATH+"/"+address.getHostAddress()+"#"+port+"#");
				System.out.println("成功");

			}

			//closeFuture() 会返回一个监听服务器关闭状态的 Future 对象。
			//后面的 .sync() 意思是：主线程，你就在这里给我进入死等状态（Block 住），什么时候这台服务器的 Channel 真正被关闭了（比如你按了 Ctrl+C 停止运行），你才能醒过来往下走！
			//有了这行代码，主线程就会永远卡在这里，从而保证底层的 Netty boss 和 work 线程池可以在后台一直安稳地接收和处理客户端的请求。
			f.channel().closeFuture().sync();
		
			System.out.println("Closed");
		} catch (Exception e) {
			e.printStackTrace();
			boss.shutdownGracefully();
			work.shutdownGracefully();
		}
	
	}

	
	@Override
	public void onApplicationEvent(ContextRefreshedEvent arg0) {
		this.start();		
	}

}
