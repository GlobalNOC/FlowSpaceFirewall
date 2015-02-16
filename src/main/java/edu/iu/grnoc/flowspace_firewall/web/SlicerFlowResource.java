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


import java.util.Iterator;
import java.util.List;

import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.iu.grnoc.flowspace_firewall.Proxy;
import edu.iu.grnoc.flowspace_firewall.Slicer;

public class SlicerFlowResource extends ServerResource{
	protected static Logger logger = LoggerFactory.getLogger(SlicerFlowResource.class);
	
	@Get("json")
	public List<OFStatistics> getSliceStats() {
		IFlowSpaceFirewallService iFSFs = (IFlowSpaceFirewallService)getContext().getAttributes().get(IFlowSpaceFirewallService.class.getCanonicalName());
		String dpidStr = (String) getRequestAttributes().get("dpid");
		Long dpid = HexString.toLong(dpidStr);
		logger.debug("Long: " + dpid);
		String sliceStr = (String) getRequestAttributes().get("slice");
		logger.debug("Slice: " + sliceStr);
		List<OFStatistics> results = iFSFs.getSlicedFlowStats(dpid, sliceStr);

		return results;
		
	}
}
