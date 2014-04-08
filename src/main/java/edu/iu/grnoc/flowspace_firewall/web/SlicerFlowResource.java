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

import edu.iu.grnoc.flowspace_firewall.FlowStatSlicer;
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
		List<OFStatistics> stats = iFSFs.getStats(dpid);
		List<OFStatistics> results = null;
		if(dpid == null || sliceStr == null){
			logger.error("Either the dpid or the slice name are null");
			return results;
		}
		List<Proxy> proxies = iFSFs.getSwitchProxies(dpid);
		if(proxies == null){
			logger.error("Unable to get proxies for switch " + dpidStr);
			return results;
		}
		Iterator <Proxy> it = proxies.iterator();
		Slicer mySlicer = null;
		while(it.hasNext()){
			Proxy p = it.next();
			logger.debug("Proxy name: " + p.getSlicer().getSliceName());
			if(p.getSlicer().getSliceName().equals(sliceStr)){
				logger.debug("MATCHED!!!!!!");
				mySlicer = p.getSlicer();
			}
		}
		
		if(mySlicer == null){
			logger.error("unable to find the slicer associated with " + sliceStr + ":" + dpidStr);
			return results;
		}
		
		try{
			results = FlowStatSlicer.SliceStats(mySlicer, stats);
		}catch(IllegalArgumentException e){
			logger.error("Problem slicing stats!");
		}
		
		return results;
		
	}
}
