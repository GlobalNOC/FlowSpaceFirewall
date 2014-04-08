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

import static org.junit.Assert.*;
import org.junit.Rule;


import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PortConfigTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void testDefaultInit() {
		PortConfig pConfig = new PortConfig();
		pConfig.setPortName("foo");
		pConfig.setVLANRange(new VLANRange());		
		assertFalse("VLAN 1 allowed",pConfig.vlanAllowed((short)1));
	}
	
	@Test
	public void testParamInit() throws IllegalArgumentException{
		
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("VLANRange not allowed to null!");
		PortConfig pConfig = new PortConfig("Foo",null);
	
	}
	
	@Test
	public void testParamInit2() throws IllegalArgumentException{
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("PortName not allowed to be null!");
		PortConfig pConfig = new PortConfig(null,new VLANRange());
		
	}
	
	@Test
	public void testParamInitProper(){
		PortConfig pConfig = new PortConfig("foo",new VLANRange());
		assertEquals("portConfig name is set properly", pConfig.getPortName(), "foo");
		assertFalse("portConfig vlan status is properly set", pConfig.vlanAllowed((short)100));
		
	}

	@Test
	public void testGetPortName(){
		PortConfig pConfig = new PortConfig();
		pConfig.setPortName("foo");
		
		assertEquals("PortConfig Name matched", "foo", pConfig.getPortName());
		pConfig.setPortName("asdfsdf");
		
		assertEquals("PortConfig Name changed and matched", "asdfsdf", pConfig.getPortName());
		assertFalse("PortConfig name is still changed", "foo" == pConfig.getPortName());
		
		
	}
	
	@Test
	public void testPortId(){
		PortConfig pConfig = new PortConfig();
		pConfig.setPortId((short)1);
		assertEquals("PortConfig Id set", (short)1,pConfig.getPortId());
		
		pConfig.setPortId((short)2);
		assertEquals("PortConfig Id changed", (short)2,pConfig.getPortId());
		
	}
	
	@Test
	public void testPortVlanAvail(){
		PortConfig pConfig = new PortConfig();
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)1,true);
		range.setVlanAvail((short)2,true);
		pConfig.setVLANRange(range);
		assertTrue("vlanRange is set properly",range.getVlanAvail((short)1));
		assertTrue("vlanRange is still set properly", range.getVlanAvail((short)2));
		assertFalse("vlanRange is still set properly", range.getVlanAvail((short)3));
	}
	

}
