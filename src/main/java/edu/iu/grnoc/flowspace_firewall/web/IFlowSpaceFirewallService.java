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
package edu.iu.grnoc.flowspace_firewall.web;

import java.util.HashMap;
import java.util.List;

import org.openflow.protocol.statistics.OFStatistics;

import edu.iu.grnoc.flowspace_firewall.Proxy;
import edu.iu.grnoc.flowspace_firewall.Slicer;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFlowSpaceFirewallService extends IFloodlightService {
	
	public boolean reloadConfig();
	//public List<OFFlowMod> getSliceFlows(String sliceName, Long dpid);
	//public HashMap<String, Object> getSliceStatus(String sliceName, Long dpid);
	public List<OFStatistics> getStats(long switchId);
	public List<OFStatistics> getSlicedFlowStats(long switchId, String sliceName);
	public List<Proxy> getSwitchProxies(long switchId);
	public List<HashMap<Long,Slicer>> getSlices();
	public List<IOFSwitch> getSwitches();
	public HashMap<Long,Slicer> getSlice(String name);
	public boolean setSliceAdminState(Long dpid, String sliceName, boolean status);

}
