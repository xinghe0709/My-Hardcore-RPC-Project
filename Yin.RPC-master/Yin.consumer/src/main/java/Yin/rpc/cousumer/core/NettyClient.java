
package Yin.rpc.cousumer.core;

import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;

import com.alibaba.fastjson.JSONObject;

import Yin.rpc.cousumer.constans.Constans;
import Yin.rpc.cousumer.handler.SimpleClientHandler;
import Yin.rpc.cousumer.param.ClientRequest;
import Yin.rpc.cousumer.param.Response;
import Yin.rpc.cousumer.zk.ServerWatcher;
import Yin.rpc.cousumer.zk.ZooKeeperFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class NettyClient {
//	public static Set<String> realServerPath = new HashSet<String>();//去重and去序列号
	public static final Bootstrap b = new Bootstrap();

	private static ChannelFuture f = null;
	
	static{
		String host = "localhost";
		int port = 8080;
		
		EventLoopGroup work = new NioEventLoopGroup();
		try {
		b.group(work)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							// 4. 流水线：防粘包 -> 字符串解码 -> 字符串编码 -> 业务处理器
							ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0]));
							ch.pipeline().addLast(new StringDecoder());//字符串解码器
							ch.pipeline().addLast(new StringEncoder());//字符串编码器
							ch.pipeline().addLast(new SimpleClientHandler());//业务逻辑处理处
						}
			});

				// 1. 连上ZK
				CuratorFramework client = ZooKeeperFactory.getClient();

				//获取指定路径下的所有节点
				List<String> serverPath = client.getChildren().forPath(Constans.SERVER_PATH);

				//客户端加上ZK监听服务器的变化
			CuratorWatcher watcher = new ServerWatcher();
			client.getChildren().usingWatcher(watcher ).forPath(Constans.SERVER_PATH);

			// 4. 遍历节点，建立 TCP 长连接
			//硬核点：它不是只连一台机器，而是把 ZK 里注册的所有机器都连上，并交给 ChannelManager 管理。
			// 这样如果一台挂了，它还能调另一台。
				for(String path :serverPath){
					String[] str = path.split("#");

					// 把活着的服务器都记在小本本上
					ChannelManager.realServerPath.add(str[0]+"#"+str[1]);

					// ⭐️ 核心变化：对每一台服务器都发起异步连接！
					ChannelFuture channnelFuture = NettyClient.b.connect(str[0], Integer.valueOf(str[1]));

					// 把连接全部扔进连接池里
					ChannelManager.addChnannel(channnelFuture);
				}

				//NettyClient 并不直接持有连接，而是通过 ChannelManager 来管理。
				if(ChannelManager.realServerPath.size()>0){
					String[] netMessageArray = ChannelManager.realServerPath.toArray()[0].toString().split("#");
					host = netMessageArray[0];
					port = Integer.valueOf(netMessageArray[1]);
				}
			
//			f = b.connect(host, port).sync();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static Response send(ClientRequest request){

		// 1. 从连接池拿一个活着的连接
		f=ChannelManager.get(ChannelManager.position);

		//2. 将对象转成 JSON 并加上 \r\n，通过网线发出去
		f.channel().writeAndFlush(JSONObject.toJSONString(request)+"\r\n");
//		f.channel().writeAndFlush("\r\n");
		Long timeOut = 60l;

		//创建 ResultFuture（储物柜），并在此进入休眠等待
		ResultFuture future = new ResultFuture(request);
//		return future.get(timeOut);
		return future.get(timeOut);

	}
	
}
