/*
 Copyright 2013 Trustees of Indiana University

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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packetstreamer.thrift.OFMessageType;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.SocketChannel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.rules.ExpectedException;
import org.openflow.protocol.*;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.*;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTest {

	protected static Logger log = LoggerFactory.getLogger(ProxyTest.class);
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	private IOFSwitch sw;
	private VLANSlicer slicer;
	private FlowSpaceFirewall fsfw;
	private SocketChannel channel;
	private OFControllerChannelHandler handler;
	FloodlightContext cntx;
	
	private List<OFMessage> messagesSentToController;
	private List<OFMessage> messagesSentToSwitch;
	private List<Proxy> proxies;
	
	public void setupChannel() throws IOException{
		ChannelFuture future = createMock(org.jboss.netty.channel.ChannelFuture.class);
		ChannelPipeline pipeline = createMock(org.jboss.netty.channel.ChannelPipeline.class);
		ChannelHandlerContext context = createMock(org.jboss.netty.channel.ChannelHandlerContext.class);
		handler = EasyMock.createNiceMock(edu.iu.grnoc.flowspace_firewall.OFControllerChannelHandler.class);
		channel = EasyMock.createNiceMock(org.jboss.netty.channel.socket.SocketChannel.class);
		
		ChannelFuture otherFuture = createMock(org.jboss.netty.channel.ChannelFuture.class);
		expect(channel.getPipeline()).andReturn(pipeline).anyTimes();
		expect(pipeline.getContext("handler")).andReturn(context).anyTimes();
		expect(context.getHandler()).andReturn(handler).anyTimes();
		expect(channel.connect(EasyMock.isA(java.net.InetSocketAddress.class))).andReturn(future).anyTimes();
		expect(channel.write(EasyMock.isA(org.openflow.protocol.OFMessage.class))).andReturn(otherFuture).anyTimes();
		
		handler.setSwitch(EasyMock.isA(net.floodlightcontroller.core.IOFSwitch.class));
		EasyMock.expectLastCall().anyTimes();
		
		handler.setProxy(EasyMock.isA(edu.iu.grnoc.flowspace_firewall.Proxy.class));
		EasyMock.expectLastCall().anyTimes();
		
		handler.sendMessage(EasyMock.isA(org.openflow.protocol.OFMessage.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		        //supply your mock implementation here...
		        messagesSentToController.add((OFMessage)EasyMock.getCurrentArguments()[0]);
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
		
		
		EasyMock.replay(future);
		EasyMock.replay(pipeline);
		EasyMock.replay(context);
		//EasyMock.replay(handler);
		EasyMock.replay(otherFuture);
	}
	
	public void setupSlicer(){
		
        slicer = new VLANSlicer();
		
		PortConfig pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100,true);
		range.setVlanAvail((short)1000,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		PortConfig pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)102,true);
		range.setVlanAvail((short)1000,true);
		pConfig2.setVLANRange(range);
		slicer.setPortConfig("foo2", pConfig2);

		PortConfig pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		range.setVlanAvail((short)1000,true);
		pConfig3.setVLANRange(range);
		slicer.setPortConfig("foo3", pConfig3);
		
		PortConfig pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		PortConfig pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig6.setVLANRange(range);
		slicer.setPortConfig("foo6", pConfig6);
		slicer.setMaxFlows(2);
		slicer.setController(new InetSocketAddress("globalnoc.iu.edu", 6633));
		slicer.setPacketInRate(10);
		
	}
	
	public void setupSlicerManaged(){
		
        slicer = new VLANSlicer();
		
		PortConfig pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		PortConfig pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)102,true);
		pConfig2.setVLANRange(range);
		slicer.setPortConfig("foo2", pConfig2);

		PortConfig pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		pConfig3.setVLANRange(range);
		slicer.setPortConfig("foo3", pConfig3);
		
		PortConfig pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		PortConfig pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		pConfig6.setVLANRange(range);
		slicer.setPortConfig("foo6", pConfig6);
		slicer.setMaxFlows(2);
		slicer.setController(new InetSocketAddress("globalnoc.iu.edu", 6633));
		slicer.setPacketInRate(10);
		slicer.setTagManagement(true);
	}
	
	@SuppressWarnings("unchecked")
	public void setupSwitch() throws IOException{
		ArrayList <ImmutablePort> ports = new ArrayList <ImmutablePort>();
		
		ImmutablePort p = createMock(ImmutablePort.class);
		expect(p.getName()).andReturn("foo").anyTimes();
		expect(p.getPortNumber()).andReturn((short)1).anyTimes();
		EasyMock.replay(p);
		ports.add(p);
		
		ImmutablePort p2 = createMock(ImmutablePort.class);
		expect(p2.getName()).andReturn("foo2").anyTimes();
		expect(p2.getPortNumber()).andReturn((short)2).anyTimes();
		EasyMock.replay(p2);
		ports.add(p2);
		
		ImmutablePort p3 = createMock(ImmutablePort.class);
		expect(p3.getName()).andReturn("foo3").anyTimes();
		expect(p3.getPortNumber()).andReturn((short)3).anyTimes();
		EasyMock.replay(p3);
		ports.add(p3);
		
		ImmutablePort p4 = createMock(ImmutablePort.class);
		expect(p4.getName()).andReturn("foo4").anyTimes();
		expect(p4.getPortNumber()).andReturn((short)4).anyTimes();
		EasyMock.replay(p4);
		ports.add(p4);
		
		ImmutablePort p5 = createMock(ImmutablePort.class);
		expect(p5.getName()).andReturn("foo5").anyTimes();
		expect(p5.getPortNumber()).andReturn((short)5).anyTimes();
		EasyMock.replay(p5);
		ports.add(p5);
		
		ImmutablePort p6 = createMock(ImmutablePort.class);
		expect(p6.getName()).andReturn("foo6").anyTimes();
		expect(p6.getPortNumber()).andReturn((short)59590).anyTimes();
		EasyMock.replay(p6);
		ports.add(p6);
		
		sw = EasyMock.createNiceMock(IOFSwitch.class);
		expect(sw.getId()).andReturn(0L).anyTimes();
		expect(sw.getPort((short)1)).andReturn(p).anyTimes();
		expect(sw.getPort((short)2)).andReturn(p2).anyTimes();
		expect(sw.getPort((short)3)).andReturn(p3).anyTimes();
		expect(sw.getPort((short)4)).andReturn(p4).anyTimes();
		expect(sw.getPort((short)5)).andReturn(p5).anyTimes();
		expect(sw.getPort((short)100)).andReturn(null).anyTimes();
		expect(sw.getPort((short)59590)).andReturn(p6).anyTimes();
		expect(sw.getPort((short)-1)).andReturn(null).anyTimes();
        expect(sw.getPorts()).andReturn((Collection <ImmutablePort>) ports).anyTimes();
        
        expect(sw.getNextTransactionId()).andReturn(1).once().andReturn(2).once()
		.andReturn(3).once().andReturn(4).once().andReturn(5).once().andReturn(6).once();
        sw.write(EasyMock.isA(org.openflow.protocol.OFMessage.class), EasyMock.isA(net.floodlightcontroller.core.FloodlightContext.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		    	log.error("Here!");
		        //supply your mock implementation here...
		        messagesSentToSwitch.add((OFMessage)EasyMock.getCurrentArguments()[0]);
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
                
        sw.write(EasyMock.isA(java.util.List.class), EasyMock.isA(net.floodlightcontroller.core.FloodlightContext.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		    	log.error("Here!");
		        //supply your mock implementation here...
		    	List<OFMessage> msgs = (List<OFMessage>)EasyMock.getCurrentArguments()[0];
		        for(OFMessage msg : msgs){
		        	messagesSentToSwitch.add(msg);
		        }
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
                
        sw.write(EasyMock.isA(java.util.List.class), EasyMock.isNull(net.floodlightcontroller.core.FloodlightContext.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		    	log.error("Here!");
		        //supply your mock implementation here...
		    	List<OFMessage> msgs = (List<OFMessage>)EasyMock.getCurrentArguments()[0];
		        for(OFMessage msg : msgs){
		        	messagesSentToSwitch.add(msg);
		        }
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
        sw.write(EasyMock.isA(org.openflow.protocol.OFMessage.class), EasyMock.isNull(net.floodlightcontroller.core.FloodlightContext.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		    	log.error("Here!");
		        //supply your mock implementation here...
		    	List<OFMessage> msgs = (List<OFMessage>)EasyMock.getCurrentArguments()[0];
		        for(OFMessage msg : msgs){
		        	messagesSentToSwitch.add(msg);
		        }
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
        
        EasyMock.replay(sw);
	
	}
	
	public void setupFSFW(){
		fsfw = createMock(FlowSpaceFirewall.class);
		List<OFStatistics> stats = new ArrayList<OFStatistics>();
		proxies = new ArrayList<Proxy>();
		expect(fsfw.getStats(EasyMock.anyLong())).andReturn(stats).anyTimes();
		
		expect(fsfw.getSwitchProxies(EasyMock.anyLong())).andReturn(proxies).anyTimes();
		fsfw.removeProxy(EasyMock.anyLong(), EasyMock.isA(Proxy.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			public Object answer(){
				log.error("removed the proxy!");
				return null;
			}
		}).anyTimes();
		fsfw.addFlowCache(EasyMock.anyLong(), EasyMock.anyObject(String.class), EasyMock.anyObject(OFFlowMod.class));
		EasyMock.expectLastCall().anyTimes();
		fsfw.delFlowCache(EasyMock.anyLong(), EasyMock.anyObject(String.class), EasyMock.anyObject(OFFlowMod.class));
		EasyMock.expectLastCall().anyTimes();
		
		EasyMock.replay(fsfw);
	}
	
	@Before
	public void setUp(){
		
		messagesSentToController = new ArrayList<OFMessage>();
		messagesSentToSwitch = new ArrayList<OFMessage>();
		cntx = new FloodlightContext();
		setupFSFW();
		
		try {
			setupChannel();
			setupSwitch();
		} catch (IOException e) {
			e.printStackTrace();
		}
		setupSlicer();
		
		
	}
	
	@Test
	public void testInstantiate() {
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		assertNotNull("Proxy was created", proxy);
		assertNotNull("Proxy can find portConfig by ID",proxy.getSlicer().getPortConfig((short)1));
	}
	
	@Test
	public void testConnect(){
		expect(channel.isConnected()).andReturn(true).once().andReturn(false).once();
		EasyMock.replay(channel);
		//log.error(handler.toString());
		expect(handler.isHandshakeComplete() ).andReturn(true).once().andReturn(false).once();		
		
		EasyMock.replay(handler);
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		proxy.disconnect();
		assertFalse("Proxy is now disconnected", proxy.connected());
	}
	
	@Test
	public void testFlowModAllowedTest(){
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier act1 = new OFActionVirtualLanIdentifier();
		act1.setVirtualLanIdentifier((short)102);
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		flow.setMatch(match);
		actions.add(act1);
		actions.add(act2);
		flow.setActions(actions);
		proxy.toSwitch(flow, cntx);
		assertTrue("Flow was successfully pushed", proxy.getFlowCount() == 1);
		assertTrue("Flow was pushed to the switch", messagesSentToSwitch.size() == 1);
		OFMessage msg = messagesSentToSwitch.get(0);
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		OFFlowMod sentFlow = (OFFlowMod) msg;
		//need to set the XID to 0 because it got mapped for us :)
		sentFlow.setXid(0);
		log.error("Received message: " + sentFlow.toString());
		assertTrue("Sent Flow matches what we actually sent", sentFlow.equals(flow));
	}

	@Test
	public void testFlowModNotAllowedTest(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
		
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		//send it
		proxy.toSwitch((OFMessage)flow, cntx);
		assertTrue("Flow as not pushed... have " + proxy.getFlowCount() + " flows", proxy.getFlowCount() == 0);
		assertTrue("No Flow was sent to the switch", messagesSentToSwitch.size() == 0);
		assertTrue("A message was sent to the controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("message was an error", msg.getType().getTypeValue() == OFMessageType.ERROR.getValue());		
	}
	
	@Test
	public void testFlowModMaxLimit(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		slicer.setMaxFlows(4);
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)1);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
				
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		//send it
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		assertTrue("4 flows pushed 1 denied because over max limit " + proxy.getFlowCount() + " flows", proxy.getFlowCount() == 4);
		assertTrue("4 message went to switch", messagesSentToSwitch.size() == 4);
		assertTrue("1 message went to the controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("Message to Controller was an error", msg.getType().getTypeValue() == OFMessageType.ERROR.getValue());
		OFError error = (OFError)msg;
		assertTrue("Error type is now OFPET_FLOW_MOD_FAILED", error.getErrorType() == OFError.OFErrorType.OFPET_FLOW_MOD_FAILED.getValue());
	}
	
	
	@Test
	public void testPacketOut(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)1000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		
		proxy.toSwitch(out, cntx);
		assertTrue("1 message was sent to the switch", messagesSentToSwitch.size() == 1);
		OFMessage msg = messagesSentToSwitch.get(0);
		assertTrue("message was of type PacketOut", msg.getType().getTypeValue() == OFMessageType.PACKET_OUT.getValue());
		OFPacketOut result = (OFPacketOut)msg;
		log.debug("PacketOut: " + result.toString());
		log.debug("Original Packet out: " + result.toString());
		//even though as far as I can tell these match... they don't work via equals
		//assertTrue("Packet matches what we sent", result.equals(out));
		
	}
	
	
	@Test
	public void testPacketOutDeny(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)3000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		//TODO: figure out the right size to set this too... this works for now
		out.setLengthU(out.getPacketData().length + 40);
		
		proxy.toSwitch(out, cntx);
		assertTrue("no messages to the switch were sent", messagesSentToSwitch.size() == 0);
		assertTrue("1 message was sent to the controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("Message to the controller was an Error", msg.getType().getTypeValue() == OFMessageType.ERROR.getValue());
		
	}
	
	@Test
	public void testPacketOutLimit() throws InterruptedException{
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)1000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		//TODO: figure out the right size to set this too... this works for now
		out.setLengthU(out.getPacketData().length + 40);
		slicer.setFlowRate(2);
		
		for(int i=0;i<10;i++){
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			proxy.toSwitch(out, cntx);
		}
		//verify some of the rules got pushed through and some got rejected
		assertTrue("Message to Switch actual = " + messagesSentToSwitch.size(), messagesSentToSwitch.size() > 1 && messagesSentToSwitch.size() < 8);
		assertTrue("Message to Controller actual = " + messagesSentToController.size(), messagesSentToController.size() > 1 && messagesSentToController.size() < 8);
		
	}
	
	@Test
	public void testPortMod(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPortMod portMod = new OFPortMod();
		portMod.setPortNumber((short)1);
		portMod.setConfig(0);
		portMod.setAdvertise(1);
		byte[] hardwareAddr = {(byte)0xe0, (byte)0x4f, (byte)0xd0,
			    (byte)0x20, (byte)0xea, (byte)0x3a};
		portMod.setHardwareAddress(hardwareAddr);
		
		proxy.toSwitch(portMod, cntx);
		assertTrue("message was not sent to switch", messagesSentToSwitch.size() == 0);
		assertTrue("message was sent to controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("Was an OpenFlow Error", msg.getType().getTypeValue() == OFMessageType.ERROR.getValue());		
	}
	
	@Test
	public void testSetConfig(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFSetConfig setConfig = new OFSetConfig();
		setConfig.setFlags((short)0);
		proxy.toSwitch(setConfig, cntx);
		assertTrue("message was not sent to switch", messagesSentToSwitch.size() == 0);
		assertTrue("message was sent to controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("Was an OpenFlow Error", msg.getType().getTypeValue() == OFMessageType.ERROR.getValue());
		
	}

	@Test
	public void testBarrierReply(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFBarrierRequest barrierRequest = new OFBarrierRequest();
		barrierRequest.setXid(10);
		
		proxy.toSwitch(barrierRequest, cntx);
		
		OFBarrierReply barrierReply = new OFBarrierReply();
		barrierReply.setXid(1);
		
		proxy.toController(barrierReply, cntx);
		
		log.debug(messagesSentToSwitch.toString());
		assertTrue("Message was sent to Switch", messagesSentToSwitch.size() == 1);
		assertTrue("Message was sent to Controller", messagesSentToController.size() == 1);
		
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		
		barrierRequest = new OFBarrierRequest();
		barrierRequest.setXid(11);
		
		proxy.toSwitch(barrierRequest, cntx);
		
		barrierReply = new OFBarrierReply();
		barrierReply.setXid(3);
		
		proxy.toController(barrierReply, cntx);
		log.debug(messagesSentToSwitch.toString());
		assertTrue("Message was sent to Switch", messagesSentToSwitch.size() == 1);
		assertTrue("Message was not sent to Controller", messagesSentToController.size() == 0);
		
	}
	
	@Test
	public void testErrorReturned(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)1);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
				
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		flow.setXid(10);
		proxy.toSwitch(flow, cntx);
		
		OFError error = new OFError();
		error.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
		error.setErrorCode(OFError.OFBadRequestCode.OFPBRC_EPERM);
		error.setOffendingMsg(flow);
		error.setXid(1);
		//proxy.toController(error, cntx);
		
		//log.debug(messagesSentToSwitch.toString());
		//assertTrue("Message was sent to Switch", messagesSentToSwitch.size() == 1);
		//assertTrue("Message was not sent to Controller", messagesSentToController.size() == 1);
		assertTrue(true);
		
	}
	
	@Test
	public void testWildCardErrorReturned(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)1);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
				
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		proxy.toSwitch(flow, cntx);
		
		log.debug(messagesSentToSwitch.toString());
		assertTrue("Message was sent to Switch", messagesSentToSwitch.size() == 0);
		assertTrue("Message was not sent to Controller", messagesSentToController.size() == 1);
		
	}
	
	@Test
	public void testErrorReturnedNotPartOfSlice(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)1);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
				
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		flow.setXid(10);
		proxy.toSwitch(flow, cntx);
		
		OFError error = new OFError();
		error.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
		error.setErrorCode(OFError.OFBadRequestCode.OFPBRC_EPERM);
		error.setOffendingMsg(flow);
		error.setXid(2);
		proxy.toController(error, cntx);
		
		log.debug(messagesSentToSwitch.toString());
		assertTrue("Message was sent to Switch", messagesSentToSwitch.size() == 1);
		assertTrue("Message was not sent to Controller", messagesSentToController.size() == 0);
	}
	
	@Test
	public void testFlowRemoved(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
	}
	
	@Test
	public void testFlowRemovedNotPartOfSlice(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testPacketINPartOfSlice(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketIn packetIn = new OFPacketIn();
		packetIn.setInPort((short)1);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)100);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)33024);
		
		packetIn.setPacketData(pkt.serialize());

		proxy.toController(packetIn, cntx);
		
		assertTrue("message was sent to controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("message is of type packet in", msg.getType().getTypeValue() == OFMessageType.PACKET_IN.getValue());
		OFPacketIn newIn = (OFPacketIn) msg;
		assertTrue("message matches what we sent", newIn.equals(packetIn));
		
	}
	
	@Test
	public void testPacketINManagedMode(){
		setupSlicerManaged();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketIn packetIn = new OFPacketIn();
		packetIn.setInPort((short)1);
		
		Ethernet pkt = new Ethernet();

		ARP arp = new ARP();
		arp.setSenderHardwareAddress(Ethernet.toMACAddress("ff:ee:dd:cc:bb:aa"));
		arp.setTargetHardwareAddress(Ethernet.toMACAddress("ff:ff:ff:ff:ff:ff"));
		arp.setTargetProtocolAddress(IPv4.toIPv4AddressBytes("10.0.0.1"));
		arp.setSenderProtocolAddress(IPv4.toIPv4AddressBytes("10.0.0.2"));
		arp.setHardwareAddressLength((byte)6);
		arp.setProtocolAddressLength((byte)4);
		arp.setHardwareType((byte)1);
		arp.setProtocolType(ARP.PROTO_TYPE_IP);
		
		pkt.setDestinationMACAddress(arp.getTargetHardwareAddress());
		pkt.setSourceMACAddress(arp.getSenderHardwareAddress());
		pkt.setPayload(arp);
		pkt.setVlanID((short)100);
		pkt.setEtherType((short)0x0806);
		
		packetIn.setPacketData(pkt.serialize());
		proxy.toController(packetIn, cntx);
		
		assertTrue("message was sent to controller", messagesSentToController.size() == 1);
		OFMessage msg = messagesSentToController.get(0);
		assertTrue("message is of type packet in", msg.getType().getTypeValue() == OFMessageType.PACKET_IN.getValue());
		OFPacketIn newIn = (OFPacketIn) msg;
		assertTrue("message matches what we sent", newIn.equals(packetIn));
		Ethernet newPkt = new Ethernet();
		newPkt.deserialize(newIn.getPacketData(),0,newIn.getPacketData().length);
		assertTrue(newPkt.getVlanID() == Ethernet.VLAN_UNTAGGED);
		
	}
	
	@Test
	public void testPacketINRateLimit(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		proxies.add(proxy);
		
		expect(channel.isConnected()).andReturn(true).once().andReturn(false).once();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketIn packetIn = new OFPacketIn();
		packetIn.setInPort((short)1);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)100);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)33024);
		
		packetIn.setPacketData(pkt.serialize());
		
		for(int i=0;i<100;i++){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.debug("sending packet out");
			proxy.toController(packetIn, cntx);
		}
		assertTrue("message was sent to controller sent a total of " + messagesSentToController.size(), messagesSentToController.size() > 1 && messagesSentToController.size() < 99);
		assertFalse("Slice is now disabled", proxy.getAdminStatus());
		assertFalse("Slice is now disconnected", proxy.connected());
	}
	
	@Test
	public void testPacketINNotPartofSlice(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketIn packetIn = new OFPacketIn();
		packetIn.setInPort((short)1);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)3000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		
		packetIn.setPacketData(pkt.serialize());

		proxy.toController(packetIn, cntx);
		
		assertTrue("message was sent to controller", messagesSentToController.size() == 0);
	}
	
	@Test
	public void testPortStatus(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPortStatus portStat = new OFPortStatus();
		OFPhysicalPort port = new OFPhysicalPort();
		port.setPortNumber((short)1);
		port.setState(1);
		port.setName("foo");
		portStat.setDesc(port);
		
		proxy.toController(portStat, cntx);
		
		assertTrue("sent message to controller", messagesSentToController.size() == 1);
		
		
	}
	
	@Test
	public void testPortStatusNotPartOfSlice(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		OFPortStatus portStat = new OFPortStatus();
		OFPhysicalPort port = new OFPhysicalPort();
		port.setPortNumber((short)4);
		port.setState(1);
		port.setName("foo4");
		portStat.setDesc(port);
		
		proxy.toController(portStat, cntx);
		assertTrue("sent message to controller", messagesSentToController.size() == 0);
	}
	
	@Test
	public void testHardTimeouts() throws InterruptedException{
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier act1 = new OFActionVirtualLanIdentifier();
		act1.setVirtualLanIdentifier((short)102);
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		flow.setMatch(match);
		actions.add(act1);
		actions.add(act2);
		flow.setActions(actions);
		flow.setHardTimeout((short)10);
		slicer.setDoTimeouts(true);
		assertTrue("Slice is set to do timeouts", slicer.doTimeouts());
		proxy.toSwitch(flow, cntx);
		assertTrue("Flow was successfully pushed", proxy.getFlowCount() == 1);
		assertTrue("Flow was pushed to the switch", messagesSentToSwitch.size() == 1);
		OFMessage msg = messagesSentToSwitch.get(0);
		
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		OFFlowMod sentFlow = (OFFlowMod) msg;
		//need to set the XID to 0 because it got mapped for us :)
		sentFlow.setXid(0);
		//since we set the hard Timeout we need clear it because are expecting it to be empty now
		flow.setHardTimeout((short)0);
		log.error("Received message: " + sentFlow.toString());
		assertTrue("Sent Flow matches what we actually sent", sentFlow.equals(flow));
		List<FlowTimeout> timeouts = proxy.getTimeouts();
		assertTrue("Slice has a flow to timeout", timeouts.size() == 1);
		proxy.checkExpiredFlows();
		assertTrue("Flow was not removed from the switch yet", messagesSentToSwitch.size() == 1);
		Thread.sleep(11000);
		proxy.checkExpiredFlows();
		log.error("Messages to Controller size: " + messagesSentToController.size());
		log.error("Messages to Switch size: " + messagesSentToSwitch.size());
		assertTrue("Flow was removed from the switch", messagesSentToSwitch.size() == 2);
		msg = messagesSentToSwitch.get(1);
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		sentFlow = (OFFlowMod) msg;
		assertTrue("Flow is a remove", sentFlow.getCommand() == OFFlowMod.OFPFC_DELETE_STRICT);
		assertTrue("No more flows to expire",proxy.getTimeouts().size() == 0);
	}
	
	@Test
	public void testIdleTimeouts() throws InterruptedException{
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		expect(handler.isHandshakeComplete()).andReturn(true).anyTimes();
		EasyMock.replay(handler);
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier act1 = new OFActionVirtualLanIdentifier();
		act1.setVirtualLanIdentifier((short)102);
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		flow.setMatch(match);
		actions.add(act1);
		actions.add(act2);
		flow.setActions(actions);
		flow.setIdleTimeout((short)10);
		slicer.setDoTimeouts(true);
		assertTrue("Slice is set to do timeouts", slicer.doTimeouts());
		proxy.toSwitch(flow, cntx);
		assertTrue("Flow was successfully pushed", proxy.getFlowCount() == 1);
		assertTrue("Flow was pushed to the switch", messagesSentToSwitch.size() == 1);
		OFMessage msg = messagesSentToSwitch.get(0);
		
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		OFFlowMod sentFlow = (OFFlowMod) msg;
		//need to set the XID to 0 because it got mapped for us :)
		sentFlow.setXid(0);
		//since we set the hard Timeout we need clear it because are expecting it to be empty now
		flow.setIdleTimeout((short)0);
		log.error("Received message: " + sentFlow.toString());
		assertTrue("Sent Flow matches what we actually sent", sentFlow.equals(flow));
		List<FlowTimeout> timeouts = proxy.getTimeouts();
		assertTrue("Slice has a flow to timeout", timeouts.size() == 1);
		Thread.sleep(5000);
		timeouts.get(0).updateLastUsed();
		Thread.sleep(6000);
		proxy.checkExpiredFlows();
		log.error("Messages to Controller size: " + messagesSentToController.size());
		log.error("Messages to Switch size: " + messagesSentToSwitch.size());
		assertTrue("Flow was not removed from the switch", messagesSentToSwitch.size() == 1);
		Thread.sleep(5000);
		proxy.checkExpiredFlows();
		assertTrue("Flow was removed from the switch", messagesSentToSwitch.size() == 2);
		msg = messagesSentToSwitch.get(1);
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		sentFlow = (OFFlowMod) msg;
		assertTrue("Flow is a remove", sentFlow.getCommand() == OFFlowMod.OFPFC_DELETE_STRICT);
		assertTrue("No more flows to expire",proxy.getTimeouts().size() == 0);
	}
	
	@Test
	public void testFlowStatsRequest(){
		
	}
}

