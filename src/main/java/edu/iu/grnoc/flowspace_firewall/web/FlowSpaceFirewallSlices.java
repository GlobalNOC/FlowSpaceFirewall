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
import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import edu.iu.grnoc.flowspace_firewall.*;

public class FlowSpaceFirewallSlices extends ServerResource{
	@Get("json")
	public HashMap<String, List<Slicer>> Slices(){
		IFlowSpaceFirewallService iFSFs = (IFlowSpaceFirewallService)getContext().getAttributes().get(IFlowSpaceFirewallService.class.getCanonicalName());
		List<HashMap<Long,Slicer>> slices = iFSFs.getSlices();
		
		HashMap<String, List<Slicer>> newSlices = new HashMap<String,List<Slicer>>();
		
		for(HashMap<Long,Slicer> slice : slices){
			for(Long dpid : slice.keySet()){
				if(newSlices.containsKey(slice.get(dpid).getSliceName())){
					List<Slicer> curSlices = newSlices.get(slice.get(dpid).getSliceName());
					curSlices.add(slice.get(dpid));
				}else{
					List<Slicer> curSlices = new ArrayList<Slicer>();
					curSlices.add(slice.get(dpid));
					newSlices.put(slice.get(dpid).getSliceName(), curSlices);
				}
			}
		}
		
		
		return newSlices;
	}

}
