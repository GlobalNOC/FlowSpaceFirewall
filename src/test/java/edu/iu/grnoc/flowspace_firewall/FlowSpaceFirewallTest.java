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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManagerTest;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManagerTest.TestLinkDiscoveryManager;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.iu.grnoc.flowspace_firewall.web.IFlowSpaceFirewallService;

@SuppressWarnings("unused")
public class FlowSpaceFirewallTest extends FloodlightTestCase{

	private TestFlowSpaceFirewall tFSFW;
	protected static Logger log = LoggerFactory.getLogger(FlowSpaceFirewall.class);
	
	public class TestFlowSpaceFirewall extends FlowSpaceFirewall {
		
	}
	
    public FlowSpaceFirewall getFSFW() {
        return tFSFW;
    }

    private IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext cntx = new FloodlightModuleContext();
        tFSFW = new TestFlowSpaceFirewall();
        MockThreadPoolService tp = new MockThreadPoolService();
        RestApiServer restApi = new RestApiServer();
        cntx.addService(IRestApiService.class, restApi);
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IFlowSpaceFirewallService.class, tFSFW);
        cntx.addService(IStorageSourceService.class, new MemoryStorageSource());
        cntx.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        restApi.init(cntx);
        tp.init(cntx);
        tFSFW.init(cntx);
        restApi.startUp(cntx);
        tp.startUp(cntx);
        tFSFW.startUp(cntx);

        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        getMockFloodlightProvider().setSwitches(switches);
        replay(sw1, sw2);
    }
    
    @Test
    public void basicTest(){
    	assertTrue(true);
    	assertNull(null);
    }
    
    @Test
	public void testConnect(){
    	assertTrue(true);
    	assertNull(null);
    }
        
}
