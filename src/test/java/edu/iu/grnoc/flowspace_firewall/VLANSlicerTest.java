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

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.expect;
import org.easymock.*;
import static org.junit.Assert.*;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.packet.Ethernet;

import org.junit.Test;
import org.junit.Before;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;

public class VLANSlicerTest {

	IOFSwitch sw;
	VLANSlicer slicer;
	PortConfig pConfig;
	PortConfig pConfig2;
	PortConfig pConfig3;
	PortConfig pConfig5;
	PortConfig pConfig6;
	
	@Before
	public void setup(){
		
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
		
		sw = createMock(IOFSwitch.class);
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
        expect(sw.getStringId()).andReturn("FF:FF:FF:FF:FF:FF").anyTimes();
        EasyMock.replay(sw);
        
        assertNotNull("switch id is not null", sw.getId());
        assertNotNull("switch port is not null", sw.getPort((short)1));
        assertEquals("ports == sw.getPorts()", ports, sw.getPorts());
        
        slicer = new VLANSlicer();
		
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100,true);
		range.setVlanAvail((short)1000,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)102,true);
		range.setVlanAvail((short)1000,true);
		pConfig2.setVLANRange(range);
		slicer.setPortConfig("foo2", pConfig2);

		pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		range.setVlanAvail((short)1000,true);
		pConfig3.setVLANRange(range);
		slicer.setPortConfig("foo3", pConfig3);
		
		pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig6.setVLANRange(range);
		slicer.setPortConfig("foo6", pConfig6);
		
		slicer.setSwitch(sw);
        
	}	
	
	
	/**
	 * tests getPortConfigById
	 */
	@Test
	public void testPortConfigById() {
		assertEquals("PortConfig for foo3 pulled out by short 3 matches", slicer.getPortConfig((short)3), pConfig3);
		assertEquals("PortConfig for foo  pulled out by short 1 matches", slicer.getPortConfig((short)1), pConfig);
		assertEquals("PortConfig for foo2 pulled out by short 2 matches", slicer.getPortConfig((short)2), pConfig2);
		assertEquals("PortConfig for foo6 pulled out by short 59590 matches", slicer.getPortConfig((short)59590), pConfig6);
	}
	
	
	/**
	 * tests getPortConfigByName
	 */
	@Test
	public void testPortConfigByName(){
		assertEquals("PortConfig for foo3 pulled out by name matches", slicer.getPortConfig("foo3"), pConfig3);
		assertEquals("PortConfig for foo  pulled out by name matches", slicer.getPortConfig("foo"), pConfig);
		assertEquals("PortConfig for foo2 pulled out by name matches", slicer.getPortConfig("foo2"), pConfig2);
	}
	
	/**
	 * tests getPortConfig when the port does not exist
	 */
	@Test
	public void testPortConfigNonExist(){
		assertNull("PortConfig for non-existent pulled out by name is null", slicer.getPortConfig("e15/4"));
		assertNull("PortConfig for non-existent pulled out by short 100 is null", slicer.getPortConfig((short)100));
	}
	
	/**
	 * tests port that exists but is not part of the slice
	 */
	@Test
	public void testPortConfigNotPartOfSlice(){
		assertNull("PortConfig for existing interface not part of slice", slicer.getPortConfig((short)4));
		assertNull("PortConfig for existing interface no part of slice name", slicer.getPortConfig("foo4"));
	}
	
	/**
	 * tests to see if a port is part of the slice
	 */
	@Test
	public void testIsPortPartOfSlice(){
		assertTrue("Port 1 is part of slice", slicer.isPortPartOfSlice((short)1));
		assertFalse("Port 4 is not part of slice", slicer.isPortPartOfSlice((short)4));
		assertTrue("Port 5 is part of slice", slicer.isPortPartOfSlice((short)5));
		assertTrue("Port 59590 is part of slice", slicer.isPortPartOfSlice((short)59590));
		
		//do the same for the names
		assertTrue("Port foo is part of slice", slicer.isPortPartOfSlice("foo"));
		assertFalse("Port foo4 is not part of slice", slicer.isPortPartOfSlice("foo4"));
		assertTrue("Port foo5 is part of slice", slicer.isPortPartOfSlice("foo5"));
		assertTrue("Port foo6 is part of slice", slicer.isPortPartOfSlice("foo6"));
		
	}
	
	/**
	 * tests packetOut event slicing
	 */
	@Test
	public void testAllowedPacketOut(){
		
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
		
		List<OFMessage> outPackets = slicer.allowedPacketOut(out);
		assertTrue("OutPacket size is correct, expected 1 got " + outPackets.size(), outPackets.size() == 1);
		
		pkt.setVlanID((short)2000);
		out.setPacketData(pkt.serialize());
		outPackets = slicer.allowedPacketOut(out);
		assertTrue("Packet out was denied", outPackets.size() == 0);
	}
	
	/**
	 * tests packetOut event slicing
	 */
	@Test
	public void testAllowedPacketOutALL(){
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort(OFPort.OFPP_ALL.getValue());
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)1000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		
		List<OFMessage> outPackets = slicer.allowedPacketOut(out);
		assertTrue("OutPacket size is correct, expected 5 got " + outPackets.size(), outPackets.size() == 5);
	}
	
	
	/**
	 * tests the allowedFlows for matches (always an action that works)
	 */
	
	@Test
	public void testIsFlowAllowedMatch(){
		
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)100);
		actions.add(setVid);
		output.setPort((short)1);
		actions.add(output);
		
		
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)100);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		flow.setMatch(match);
		flow.setActions(actions);

		
		List <OFFlowMod> flows = slicer.allowedFlows(flow);
		assertTrue("flows is the right size " + flows.size(), flows.size() == 1);
		assertEquals("flow was allowed and matches", flow, flows.get(0));
		
		
		flow = new OFFlowMod();
		match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)200);
		flow.setMatch(match);
		flows = slicer.allowedFlows(flow);
		flow.setActions(actions);
		assertEquals("flow was denied",flows.size(), 0);
		
		flow = new OFFlowMod();
		match = new OFMatch();
		match.setInputPort((short)1);
		flow.setMatch(match);
		flow.setActions(actions);
		flows = slicer.allowedFlows(flow);
		assertEquals("flow was denied",flows.size(),0);
		
		flow = new OFFlowMod();
		match = new OFMatch();
		match.setInputPort((short)0);
		match.setDataLayerVirtualLan((short)1000);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		flow.setMatch(match);
		flow.setActions(actions);
		flows = slicer.allowedFlows(flow);
		assertNotNull("flow was denied",flows);
		assertTrue("Flow Expansion worked", flows.size() == 5);
		
		flow = new OFFlowMod();
		match = new OFMatch();
		match.setInputPort((short)-1);
		match.setDataLayerVirtualLan((short)1000);
		flow.setMatch(match);
		flow.setActions(actions);
		flows = slicer.allowedFlows(flow);
		assertEquals("flow was denied",flows.size(),0);
		
		flow = new OFFlowMod();
		match = new OFMatch();
		match.setInputPort((short)2);
		match.setDataLayerVirtualLan((short)0);
		flow.setMatch(match);
		flow.setActions(actions);
		flows = slicer.allowedFlows(flow);
		assertEquals("flow was denied",flows.size(),0);
		
	}
	
	/*
	 * tests isFlowModAllowed for simple cases
	 */
	@Test
	public void testIsFlowModAllowedSimple(){
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)1000);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		flow.setMatch(match);
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)102);		
		OFActionOutput output = new OFActionOutput();
		output.setPort((short)2);
		actions.add(setVid);
		actions.add(output);
		flow.setActions(actions);
		
		List <OFFlowMod> flows = slicer.allowedFlows(flow);
		assertTrue("flows is the right size", flows.size() == 1);
		assertEquals("flow was allowed and matches", flow, flows.get(0));
		
	}
	
	/**
	 * tests isFlowModAllowed for expansions
	 */
	@Test 
	public void testIsFlowModAllowedExpansions(){
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)100);
		actions.add(setVid);
		output.setPort((short)1);
		actions.add(output);
		
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)0);
		match.setDataLayerVirtualLan((short)1000);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		flow.setMatch(match);
		flow.setActions(actions);
		List<OFFlowMod>flows = slicer.allowedFlows(flow);
		assertTrue("flow was denied",flows.size() != 0);
		assertTrue("Flow Expansion worked", flows.size() == 5);

		PortConfig tmpPConfig = new PortConfig();
		tmpPConfig.setPortName("foo4");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100, true);
		range.setVlanAvail((short)1000, true);
		tmpPConfig.setVLANRange(range);
		slicer.setPortConfig("foo4", tmpPConfig);
		

		flows = slicer.allowedFlows(flow);
		assertTrue("flow was denied",flows.size() != 0);
		assertTrue("Flow Expansion worked, and then we detected it was total number of ports so changed to just 1, got " + flows.size(), flows.size() == 1);
		
	}

	/**
	 * tests isFlowModAllowed for expansions
	 */
	@Test 
	public void testIsFlowModALLAllowedExpansions(){
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)1000);
		actions.add(setVid);
		output.setPort(OFPort.OFPP_ALL.getValue());
		actions.add(output);
		
		PortConfig pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)104, true);
		range.setVlanAvail((short)1000, true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)1000);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		flow.setMatch(match);
		flow.setActions(actions);
		
		List<OFFlowMod>flows = slicer.allowedFlows(flow);
		
		assertTrue("flow was denied",flows.size() != 0);
		assertTrue("Flow Expansion worked had a size of " + flows.size(), flows.size() == 1);
		
		OFFlowMod expanded = flows.get(0);
		assertTrue("Correct number of actions", expanded.getActions().size() == 5);
		
	}
	
	/**
	 * tests isFlowModAllowed for expansions
	 */
	@Test 
	public void testIsFlowModALLAllowedWildcardExpansions(){
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)1000);
		actions.add(setVid);
		output.setPort(OFPort.OFPP_ALL.getValue());
		actions.add(output);
		
		PortConfig pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)104, true);
		range.setVlanAvail((short)1000, true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		PortConfig pConfig4 = new PortConfig();
		pConfig4.setPortName("foo4");
		range = new VLANRange();
		range.setVlanAvail((short)104, true);
		range.setVlanAvail((short)1000, true);
		pConfig4.setVLANRange(range);
		slicer.setPortConfig("foo4", pConfig4);
		
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)0);
		match.setDataLayerVirtualLan((short)1000);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		flow.setMatch(match);
		flow.setActions(actions);
		
		List<OFFlowMod>flows = slicer.allowedFlows(flow);
		
		assertTrue("flow was denied",flows.size() != 0);
		assertTrue("Flow Expansion worked had a size of " + flows.size(), flows.size() == 1);
		
		OFFlowMod expanded = flows.get(0);
		assertTrue("Correct number of actions expected 5 got " + expanded.getActions().size(), expanded.getActions().size() == 2);
		
	}
	
	/**
	 * tests the hasOverlap method to 
	 */
	@Test
	public void testHasOverlap(){
		VLANSlicer otherSlicer = new VLANSlicer();
		
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		range.setVlanAvail((short)1001,true);
		pConfig.setVLANRange(range);
		otherSlicer.setPortConfig("foo", pConfig);
		
		pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		range.setVlanAvail((short)1003,true);
		pConfig2.setVLANRange(range);
		otherSlicer.setPortConfig("foo2", pConfig2);

		pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)104,true);
		range.setVlanAvail((short)1005,true);
		pConfig3.setVLANRange(range);
		otherSlicer.setPortConfig("foo3", pConfig3);
		
		pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)106,true);
		range.setVlanAvail((short)1006,true);
		pConfig5.setVLANRange(range);
		otherSlicer.setPortConfig("foo5", pConfig5);
		
		pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)107,true);
		range.setVlanAvail((short)1007,true);
		pConfig6.setVLANRange(range);
		otherSlicer.setPortConfig("foo6", pConfig6);
		
		assertFalse(slicer.hasOverlap(otherSlicer));
		
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		range = new VLANRange();
		range.setVlanAvail((short)100,true);
		range.setVlanAvail((short)1000,true);
		pConfig.setVLANRange(range);
		otherSlicer.setPortConfig("foo", pConfig);
		assertTrue(slicer.hasOverlap(otherSlicer));
	}
	
	/*
	 * tests managed tag mode for flow mods
	 */
	@Test
	public void testManagedFlowMod(){
		VLANSlicer otherSlicer = new VLANSlicer();
		otherSlicer.setTagManagement(true);
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		pConfig.setVLANRange(range);
		otherSlicer.setPortConfig("foo", pConfig);
		
		pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		pConfig2.setVLANRange(range);
		otherSlicer.setPortConfig("foo2", pConfig2);

		pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)104,true);
		pConfig3.setVLANRange(range);
		otherSlicer.setPortConfig("foo3", pConfig3);
		
		pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)106,true);
		pConfig5.setVLANRange(range);
		otherSlicer.setPortConfig("foo5", pConfig5);
		
		pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)107,true);
		pConfig6.setVLANRange(range);
		otherSlicer.setPortConfig("foo6", pConfig6);
		
		otherSlicer.setSwitch(sw);
		
		OFFlowMod flowMod = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)3);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		flowMod.setMatch(match);
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput out = new OFActionOutput();
		out.setPort((short)1);
		actions.add(out);
		flowMod.setActions(actions);
		flowMod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
		List<OFFlowMod> managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 1);
		OFFlowMod processedFlow = managedFlows.get(0);
		assertTrue(processedFlow.getMatch().getDataLayerVirtualLan() == 104);
		List<OFAction> processedActions = processedFlow.getActions();
		assertTrue(processedActions.size() == 2);
		assertTrue(processedActions.get(0).getType() == OFActionType.SET_VLAN_ID);
		OFActionVirtualLanIdentifier set_vlan_vid = (OFActionVirtualLanIdentifier)processedActions.get(0);
		assertTrue(set_vlan_vid.getVirtualLanIdentifier() == 101);
		
		actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier set_vlan = new OFActionVirtualLanIdentifier();
		set_vlan.setVirtualLanIdentifier((short)100);
		actions.add(set_vlan);
		actions.add(out);
		flowMod.setActions(actions);
		flowMod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionVirtualLanIdentifier.MINIMUM_LENGTH));
		managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 0);
		
	}
	
	/*
	 * tests managed tag mode for flow mods
	 */
	@Test
	public void testManagedFlowModWithTag(){
		VLANSlicer otherSlicer = new VLANSlicer();
		otherSlicer.setTagManagement(true);
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		pConfig.setVLANRange(range);
		otherSlicer.setPortConfig("foo", pConfig);
		
		pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		pConfig2.setVLANRange(range);
		otherSlicer.setPortConfig("foo2", pConfig2);

		pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)104,true);
		pConfig3.setVLANRange(range);
		otherSlicer.setPortConfig("foo3", pConfig3);
		
		pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)106,true);
		pConfig5.setVLANRange(range);
		otherSlicer.setPortConfig("foo5", pConfig5);
		
		pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)107,true);
		pConfig6.setVLANRange(range);
		otherSlicer.setPortConfig("foo6", pConfig6);
		
		otherSlicer.setSwitch(sw);
		
		OFFlowMod flowMod = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)3);
		match.setDataLayerVirtualLan((short)200);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		flowMod.setMatch(match);
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput out = new OFActionOutput();
		out.setPort((short)1);
		actions.add(out);
		flowMod.setActions(actions);
		flowMod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
		List<OFFlowMod> managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 0);
		
		actions.clear();
		OFActionVirtualLanIdentifier set_vlan_vid = new OFActionVirtualLanIdentifier();
		set_vlan_vid.setVirtualLanIdentifier((short)100);
		actions.add(set_vlan_vid);
		actions.add(out);
		flowMod.setActions(actions);
		flowMod.getMatch().setDataLayerVirtualLan((short)0);
		flowMod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionVirtualLanIdentifier.MINIMUM_LENGTH));
		managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 0);
	}
	
	/*
	 * tests managed tag mode for flow mods
	 */
	@Test
	public void testManagedFlowModWithNoPort(){
		VLANSlicer otherSlicer = new VLANSlicer();
		otherSlicer.setTagManagement(true);
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		pConfig.setVLANRange(range);
		otherSlicer.setPortConfig("foo", pConfig);
		
		pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		pConfig2.setVLANRange(range);
		otherSlicer.setPortConfig("foo2", pConfig2);

		pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)104,true);
		pConfig3.setVLANRange(range);
		otherSlicer.setPortConfig("foo3", pConfig3);
		
		pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)106,true);
		pConfig5.setVLANRange(range);
		otherSlicer.setPortConfig("foo5", pConfig5);
		
		pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)107,true);
		pConfig6.setVLANRange(range);
		otherSlicer.setPortConfig("foo6", pConfig6);
		
		otherSlicer.setSwitch(sw);
		
		OFFlowMod flowMod = new OFFlowMod();
		OFMatch match = new OFMatch();
		match.setInputPort((short)4);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		flowMod.setMatch(match);
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput out = new OFActionOutput();
		out.setPort((short)1);
		actions.add(out);
		flowMod.setActions(actions);
		flowMod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
		List<OFFlowMod> managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 0);
		
		flowMod.getMatch().setInputPort((short)3);
		out.setPort((short)4);
		actions.clear();
		actions.add(out);
		flowMod.setActions(actions);
		managedFlows = otherSlicer.managedFlows(flowMod);
		assertTrue(managedFlows.size() == 0);
	}
	
	/**
	 * tests packetOut event slicing
	 */
	@Test
	public void testAllowedPacketOutManagedMode(){
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		slicer.setTagManagement(true);
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		List<OFMessage> outPackets = slicer.managedPacketOut(out);
		assertTrue("OutPacket size is correct, expected 1 got " + outPackets.size(), outPackets.size() == 1);
		OFPacketOut managedPacket = (OFPacketOut) outPackets.get(0);
		List<OFAction> acts = managedPacket.getActions();
		assertTrue("Action list size is 2!!",acts.size() == 2);
		assertTrue("first action is set vlan id",acts.get(0).getType() == OFActionType.SET_VLAN_ID);
		OFActionVirtualLanIdentifier set_vlan_vid = (OFActionVirtualLanIdentifier) acts.get(0);
		assertTrue("VLAN Tag set is correct",set_vlan_vid.getVirtualLanIdentifier() == 101);
		assertTrue("Second action type is OUTPUT", acts.get(1).getType() == OFActionType.OUTPUT);
		OFActionOutput outPort = (OFActionOutput) acts.get(1);
		assertTrue("output port is 1", outPort.getPort() == 1);
		
		pkt.setVlanID((short)2000);
		out.setPacketData(pkt.serialize());
		outPackets = slicer.managedPacketOut(out);
		assertTrue("Packet out was denied", outPackets.size() == 0);
	}
	
	/**
	 * tests packetOut event slicing
	 */
	@Test
	public void testAllowedPacketOutALLManaged(){
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort(OFPort.OFPP_ALL.getValue());
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		
		slicer.setTagManagement(true);
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)101,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		List<OFMessage> outPackets = slicer.managedPacketOut(out);
		assertTrue("OutPacket size is correct, expected 5 got " + outPackets.size(), outPackets.size() == 5);
		//TODO: need to verify the packet outs are what we want		
		
		pkt.setVlanID((short)2000);
		out.setPacketData(pkt.serialize());
		outPackets = slicer.managedPacketOut(out);
		assertTrue("Packet out was denied", outPackets.size() == 0);
		
	}
	
	
}
