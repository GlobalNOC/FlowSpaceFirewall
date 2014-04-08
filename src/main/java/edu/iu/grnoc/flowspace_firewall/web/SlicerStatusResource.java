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
import java.util.Iterator;
import java.util.List;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.iu.grnoc.flowspace_firewall.Proxy;
import edu.iu.grnoc.flowspace_firewall.Slicer;

public class SlicerStatusResource extends ServerResource{
	protected static Logger logger = LoggerFactory.getLogger(SlicerStatusResource.class);
	@Get("json")
	public HashMap<String, Object> getSliceStatus(){
		IFlowSpaceFirewallService iFSFs = (IFlowSpaceFirewallService)getContext().getAttributes().get(IFlowSpaceFirewallService.class.getCanonicalName());
		String dpidStr = (String) getRequestAttributes().get("dpid");
		Long dpid = HexString.toLong(dpidStr);
		String sliceStr = (String) getRequestAttributes().get("slice");
		
		List<Proxy> proxies = iFSFs.getSwitchProxies(dpid);
		HashMap<String, Object> results = new HashMap<String, Object>();
		if(proxies == null){
			logger.info("Unable to fetch proxies for switch " + dpidStr + " it is not connected");
			//this means switch is not connected... so grab the slice/switch combo if it exists
			HashMap <Long, Slicer> slice = iFSFs.getSlice(sliceStr);
			if(slice == null){
				results.put("Error", "No slice by name: " + sliceStr);
				return results;
			}
			
			if(!slice.containsKey(dpid)){
				logger.warn("Switch " + dpidStr + " is not part of slice " + sliceStr);
				results.put("Error",  "Switch " + dpidStr + " is not part of slice " + sliceStr);
				return results;
			}
			
			Slicer mySlice = slice.get(dpid);
			results.put("flow_rate", "N/A");
			results.put("total_flows", 0);
			results.put("max_flows", mySlice.getMaxFlows());
			results.put("connected",  false);
			
		}
		
		Iterator <Proxy> it = proxies.iterator();
		Proxy myProxy = null;
		while(it.hasNext()){
			Proxy p = it.next();
			if(p.getSlicer().getSliceName().equals(sliceStr)){
				myProxy = p;
			}
		}
		
		if(myProxy == null){
			logger.warn("Unable to find slice/proxy for " + sliceStr + ":" + dpidStr);
			results.put("Error",  "Switch " + dpidStr + " is not part of slice " + sliceStr);
			return results;
		}
		
		results.put("flow_rate", myProxy.getSlicer().getRate() );
		results.put("total_flows", myProxy.getFlowCount());
		results.put("max_flows",  myProxy.getSlicer().getMaxFlows());
		results.put("connected", myProxy.connected());
		return results;
		
	}	
}
