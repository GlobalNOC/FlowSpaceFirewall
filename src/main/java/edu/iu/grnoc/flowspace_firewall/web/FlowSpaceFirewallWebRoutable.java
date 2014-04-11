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


import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class FlowSpaceFirewallWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/admin/reloadConfig/json",FlowSpaceFirewallResource.class);
		router.attach("/status/{slice}/{dpid}/json",SlicerStatusResource.class);
		router.attach("/flows/{slice}/{dpid}/json", SlicerFlowResource.class);
		router.attach("/admin/switches/json",FlowSpaceFirewallSwitches.class);
		router.attach("/admin/slices/json", FlowSpaceFirewallSlices.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/fsfw";
	}

}
