/*
 Copyright 2014 Trustees of Indiana University

   Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.iu.grnoc.flowspace_firewall;

import edu.iu.grnoc.flowspace_firewall.Proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import net.floodlightcontroller.core.internal.OFMessageDecoder;
import net.floodlightcontroller.core.internal.OFMessageEncoder;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects switches to the controller on whatever the timer
 * interval says to do it
 * @author aragusa
 *
 */
public class ControllerConnector extends TimerTask {
    private int workerThreads = 10;    
	private HashMap <Long, List<Proxy>> proxies;
	NioClientSocketChannelFactory channelCreator;
	Timer timer;
	private static final Logger log = LoggerFactory.getLogger(ControllerConnector.class);
	
	public ControllerConnector(){
		proxies = new HashMap<Long, List<Proxy>>();

		channelCreator = new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(), workerThreads);
		timer = new HashedWheelTimer();
		
	}
	
	
	/**
	 * creates a new pipeline for interacting with the
	 * controller.  This is where the controllerHandler and
	 * the timeouthandler come into play
	 * @return the pipeline (ChannelPipeline) for a new Socket.
	 */
	private ChannelPipeline getPipeline(){
		ChannelPipeline pipe = Channels.pipeline();
	    
		ChannelHandler idleHandler = new IdleStateHandler(timer, 20, 25, 0);
	    ChannelHandler readTimeoutHandler = new ReadTimeoutHandler(timer, 30);
	    OFControllerChannelHandler controllerHandler = new OFControllerChannelHandler();
		
        pipe.addLast("ofmessagedecoder", new OFMessageDecoder());
        pipe.addLast("ofmessageencoder", new OFMessageEncoder());
        pipe.addLast("idle", idleHandler);
        pipe.addLast("timeout", readTimeoutHandler);
        pipe.addLast("handshaketimeout",
                     new ControllerHandshakeTimeoutHandler(controllerHandler, timer, 15));
        pipe.addLast("handler", controllerHandler);
        return pipe;
	}
	
	/**
	 * everytime the timer fires run this!
	 * Looks through every proxy and determines if the proxy needs to 
	 * attempt to connect to the controller.
	 */
	public void run(){
		log.debug("Looking for controllers not currently connected");
		Iterator <Long> it = proxies.keySet().iterator();
		while(it.hasNext()){
			List <Proxy> ps = proxies.get(it.next());
			Iterator <Proxy> proxyIt = ps.iterator();
			while(proxyIt.hasNext()){
				Proxy p = proxyIt.next();
				log.debug("Proxy for " + p.getSwitch().getStringId() + " " + p.getSlicer().getControllerAddress().toString() + " is connected: " + p.connected());
				if(!p.connected() && p.getAdminStatus()){
					log.warn("Creating new Channel to " + p.getSlicer().getControllerAddress().toString() + " for switch: " + p.getSwitch().getStringId());
					SocketChannel controller_channel = channelCreator.newChannel(getPipeline());
					p.connect(controller_channel);
				}
			}
			
		}
	}
	
	/**
	 * get a list of proxies for a given switch
	 * @param switchId (Long)
	 * @return List <Proxy> getSwitchProxies
	 * synchronized for thread safty
	 */
	public synchronized List <Proxy> getSwitchProxies(Long switchId){
		log.debug("Looking for switchID: " + switchId);
		return proxies.get(switchId);
	}
	
	/**
	 * adds a proxy to the 
	 * @param switchId
	 * @param p
	 * synchronized for thread safty
	 */
	public synchronized void addProxy(Long switchId, Proxy p){
		if(!proxies.containsKey(switchId)){
			List <Proxy> proxyList = new ArrayList<Proxy>();
			proxyList.add(p);
			proxies.put(switchId, proxyList);
		}else{
			proxies.get(switchId).add(p);
		}
	}
	
	/**
	 * removes a proxy from the list
	 * @param switchId
	 * @param p
	 * synchronized for thread safty
	 */
	public synchronized void removeProxy(Long switchId, Proxy p){
		if(proxies.containsKey(switchId)){
			List <Proxy> proxyList = proxies.get(switchId);
			proxyList.remove(p);
		}
	}
	
	/**
	 * 
	 */
	public synchronized List<Proxy> getAllProxies(){
		List <Proxy> allProxies = new ArrayList<Proxy>();
		for(Long dpid : proxies.keySet()){
			allProxies.addAll(proxies.get(dpid));
		}
		return allProxies;
	}
}
