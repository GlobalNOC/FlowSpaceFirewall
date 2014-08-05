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

import org.junit.Test;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.openflow.protocol.OFFlowMod;

public class FlowTimeoutTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testFlowTimeoutInit(){
		FlowTimeout timeout = new FlowTimeout(new OFFlowMod(), 10, false);
		assertNotNull("timeout was properly created",timeout);
		assertTrue("is an idle timemout", timeout.isHard() == false);
		assertTrue("packet count init was 0",timeout.getPacketCount() == 0);
		assertTrue("is expired == false", timeout.isExpired() == false);
		
		timeout = new FlowTimeout(new OFFlowMod(), 100, true);
		assertNotNull("timeout was properly created",timeout);
		assertTrue("is an idle timemout", timeout.isHard());
		assertTrue("packet count init was 0",timeout.getPacketCount() == 0);
		assertTrue("is expired == false", timeout.isExpired() == false);
	
	}
	
	@Test
	public void testFlowTimeoutSetPacketCount(){
		FlowTimeout timeout = new FlowTimeout(new OFFlowMod(), 1, false);
		timeout.setPacketCount((long)100);
		assertTrue("packet count was updated",timeout.getPacketCount() == 100);
		timeout.setPacketCount((long)1000);
		assertTrue("packet count was updated again", timeout.getPacketCount() == 1000);
	}
	
	@Test
	public void testFlowTimeoutTimeout() throws InterruptedException{
		FlowTimeout timeout = new FlowTimeout(new OFFlowMod(), 1, false);
		assertFalse("Not timed out",timeout.isExpired());
		Thread.sleep(2000);
		assertTrue("Now timed out",timeout.isExpired());
	}
	
	@Test
	public void testFlowTimeoutUpdateLastUsed() throws InterruptedException{
		FlowTimeout timeout = new FlowTimeout(new OFFlowMod(), 10, false);
		assertFalse("Not timed out", timeout.isExpired());
		Thread.sleep(5000);
		timeout.updateLastUsed();
		Thread.sleep(7000);
		assertFalse("Still not timed out",timeout.isExpired());
		Thread.sleep(4000);
		assertTrue("Now timed out",timeout.isExpired());
		
	}
	
}
