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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.easymock.*;

import static org.junit.Assert.*;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import org.junit.Test;
import org.junit.Before;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class FlowStatSlicerTest {

	List <OFStatistics> allowedStats;
	List <OFStatistics> noAllowedStats;
	List <OFStatistics> mixedStats;
	List <OFStatistics> managedStats;
	IOFSwitch sw;
	VLANSlicer slicer;
	VLANSlicer managedSlicer;
	PortConfig pConfig;
	PortConfig pConfig2;
	PortConfig pConfig3;
	PortConfig pConfig5;
	protected static Logger log = LoggerFactory.getLogger(FlowStatSlicerTest.class);
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	public void buildAllowedStats(){
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setPort((short)1);
		actions.add(output);
		
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)100);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		//all allowed stats
		allowedStats = new ArrayList<OFStatistics>();
		OFFlowStatisticsReply stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123126L);
		allowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)2);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)2);
		match.setDataLayerVirtualLan((short)102);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123125L);
		allowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)3);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)3);
		match.setDataLayerVirtualLan((short)103);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123124L);
		allowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)5);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)5);
		match.setDataLayerVirtualLan((short)105);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		allowedStats.add(stat);
	}

	public void buildManagedTagStats(){
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier setVlanID = new OFActionVirtualLanIdentifier();
		setVlanID.setVirtualLanIdentifier((short)200);
		actions.add(setVlanID);
		OFActionOutput output = new OFActionOutput();
		output.setPort((short)1);
		actions.add(output);
		
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)200);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		//all allowed stats
		managedStats = new ArrayList<OFStatistics>();
		OFFlowStatisticsReply stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123126L);
		managedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)2);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)2);
		match.setDataLayerVirtualLan((short)200);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123125L);
		managedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)3);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)3);
		match.setDataLayerVirtualLan((short)200);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123124L);
		managedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)5);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)5);
		match.setDataLayerVirtualLan((short)200);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		managedStats.add(stat);
		
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)5);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)5);
		match.setDataLayerVirtualLan((short)100);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		managedStats.add(stat);
	}

	
	
	public void buildNoAllowedStats(){
		//no allowed stats
		noAllowedStats = new ArrayList<OFStatistics>();

		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setPort((short)1);
		actions.add(output);
		
		OFMatch match = new OFMatch();
		match.setInputPort((short)1);
		match.setDataLayerVirtualLan((short)300);
		
		OFFlowStatisticsReply stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123126L);
		noAllowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)2);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)2);
		match.setDataLayerVirtualLan((short)202);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123125L);
		noAllowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)3);
		OFActionVirtualLanIdentifier setVid = new OFActionVirtualLanIdentifier();
		setVid.setVirtualLanIdentifier((short)300);
		actions.add(setVid);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)3);
		match.setDataLayerVirtualLan((short)103);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123124L);
		noAllowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)4);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)5);
		match.setDataLayerVirtualLan((short)105);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		noAllowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)4);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)5);
		match.setDataLayerVirtualLan((short)105);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		noAllowedStats.add(stat);
		
		actions = new ArrayList<OFAction>();
		output = new OFActionOutput();
		output.setPort((short)5);
		actions.add(output);
		match = new OFMatch();
		match.setInputPort((short)4);
		match.setDataLayerVirtualLan((short)105);
		match.setWildcards(match.getWildcardObj().matchOn(Flag.DL_VLAN));
		match.setWildcards(match.getWildcardObj().matchOn(Flag.IN_PORT));
		
		stat = new OFFlowStatisticsReply();
		stat.setActions(actions);
		stat.setMatch(match);
		stat.setByteCount(123123L);
		noAllowedStats.add(stat);
	}
	
	public void buildMixedStats(){
		//a mixed set of stats some allowed some not allowed
		mixedStats = new ArrayList<OFStatistics>();
	}
	
	
	@Before
	public void buildStats(){
		
		buildAllowedStats();
		buildNoAllowedStats();
		buildMixedStats();
		buildManagedTagStats();

		
	}
	
	@Before
	public void buildSlicer(){
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
		
		sw = createMock(IOFSwitch.class);
		expect(sw.getId()).andReturn(0L).anyTimes();
		expect(sw.getStringId()).andReturn("0000000").anyTimes();
		expect(sw.getPort((short)1)).andReturn(p).anyTimes();
		expect(sw.getPort((short)2)).andReturn(p2).anyTimes();
		expect(sw.getPort((short)3)).andReturn(p3).anyTimes();
		expect(sw.getPort((short)4)).andReturn(p4).anyTimes();
		expect(sw.getPort((short)5)).andReturn(p5).anyTimes();
		expect(sw.getPort((short)100)).andReturn(null).anyTimes();
		expect(sw.getPort((short)-1)).andReturn(null).anyTimes();
        expect(sw.getPorts()).andReturn((Collection <ImmutablePort>) ports).anyTimes();
        EasyMock.replay(sw);
        
        assertNotNull("switch id is not null", sw.getId());
        assertNotNull("switch port is not null", sw.getPort((short)1));
        assertEquals("ports == sw.getPorts()", ports, sw.getPorts());
        
        slicer = new VLANSlicer();
        managedSlicer = new VLANSlicer();
        managedSlicer.setTagManagement(true);
		
		pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100,true);
		range.setVlanAvail((short)1000,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		PortConfig managedPConfig = new PortConfig();
		range = new VLANRange();
		range.setVlanAvail((short)200, true);
		managedPConfig.setVLANRange(range);
		managedSlicer.setPortConfig("foo", managedPConfig);
		
		pConfig2 = new PortConfig();
		pConfig.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)102,true);
		range.setVlanAvail((short)1000,true);
		pConfig2.setVLANRange(range);
		slicer.setPortConfig("foo2", pConfig2);
		
		managedPConfig = new PortConfig();
		range = new VLANRange();
		range.setVlanAvail((short)200, true);
		managedPConfig.setVLANRange(range);
		managedSlicer.setPortConfig("foo2", managedPConfig);
		
		pConfig3 = new PortConfig();
		pConfig.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		range.setVlanAvail((short)1000,true);
		pConfig3.setVLANRange(range);
		slicer.setPortConfig("foo3", pConfig3);
		
		managedPConfig = new PortConfig();
		range = new VLANRange();
		range.setVlanAvail((short)200, true);
		managedPConfig.setVLANRange(range);
		managedSlicer.setPortConfig("foo3", managedPConfig);
		
		pConfig5 = new PortConfig();
		pConfig.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		managedPConfig = new PortConfig();
		range = new VLANRange();
		range.setVlanAvail((short)200, true);
		managedPConfig.setVLANRange(range);
		managedSlicer.setPortConfig("foo5", managedPConfig);
		
		slicer.setSwitch(sw);
		managedSlicer.setSwitch(sw);
		
	}
	
	@Test
	public void testSliceStatsAllAllowed() {
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(slicer, allowedStats);
		assertEquals("Number of sliced stat is same as number of total stats",  allowedStats.size(),slicedStats.size());
	}
	
	@Test
	public void testSliceStatsMixed(){
		//List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(slicer, mixedStats);
		
	}
	
	@Test
	public void testSliceStatsNull(){
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("FlowStat slicer got null stats!!");
		@SuppressWarnings("unused")
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(slicer, null);

	}
	
	@Test
	public void testSliceStatsNonAllowed(){
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(slicer, noAllowedStats);
		assertNotNull("Sliced Stats with no allowed stats returned ok",slicedStats);
		assertEquals("Sliced stats", slicedStats.size(),0);
	}
	
	@Test
	public void testSliceStatsNoStats(){
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(slicer, new ArrayList<OFStatistics>());
		assertNotNull("Sliced Stats with no allowed stats returned ok",slicedStats);
		assertEquals("Sliced stats", 0, slicedStats.size());
	}
	
	@Test
	public void testsManagedTagSliceStatsNoStats(){
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(managedSlicer,  new ArrayList<OFStatistics>());
		assertNotNull("Sliced Stats with no allowed stats returend ok", slicedStats);
		assertEquals("Sliced stats", 0, slicedStats.size());
	}
	
	@Test
	public void testsManagedTagSliceStats(){
		List <OFStatistics> slicedStats = FlowStatSlicer.SliceStats(managedSlicer,  managedStats);
		assertNotNull("Sliced Stats with no allowed stats returend ok", slicedStats);
		assertEquals("Sliced stats", 4, slicedStats.size());
		
		OFFlowStatisticsReply stat = (OFFlowStatisticsReply) slicedStats.get(0);
		assertTrue( stat.getMatch().getWildcardObj().isWildcarded(Wildcards.Flag.DL_VLAN));
		
		assertTrue( stat.getActions().size() == 1);
		assertTrue( stat.getActions().get(0).getType() == OFActionType.OUTPUT);
		
	}

}
