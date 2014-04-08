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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import edu.iu.grnoc.flowspace_firewall.Slicer;

public class FlowSpaceFirewallSwitches extends ServerResource{
	@Get("json")
	public HashMap<Long,List<Slicer>> getSwitches(){
		IFlowSpaceFirewallService iFSFs = (IFlowSpaceFirewallService)getContext().getAttributes().get(IFlowSpaceFirewallService.class.getCanonicalName());
		List<HashMap<Long,Slicer>> slices = iFSFs.getSlices();
		HashMap<Long,List<Slicer>> results = new HashMap<Long,List<Slicer>>();
		Iterator <HashMap<Long,Slicer>> sliceIt = slices.iterator();
		while(sliceIt.hasNext()){
			HashMap<Long,Slicer> slice = sliceIt.next();
			Iterator<Long> dpidIt = slice.keySet().iterator();
			while(dpidIt.hasNext()){
				Long dpid = dpidIt.next();
				if(results.containsKey(dpid)){
					results.get(dpid).add(slice.get(dpid));
				}else{
					ArrayList<Slicer> sliceList = new ArrayList<Slicer>();
					sliceList.add(slice.get(dpid));
					results.put(dpid, sliceList);
				}
			}
		}
		return results;
	}
}
