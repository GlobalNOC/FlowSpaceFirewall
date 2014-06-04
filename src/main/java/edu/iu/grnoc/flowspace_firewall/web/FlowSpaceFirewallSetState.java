package edu.iu.grnoc.flowspace_firewall.web;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class FlowSpaceFirewallSetState extends ServerResource{

	@Get("set_state")
	public boolean setSliceStatus(){
		IFlowSpaceFirewallService iFSFs = (IFlowSpaceFirewallService)getContext().getAttributes().get(IFlowSpaceFirewallService.class.getCanonicalName());
		String dpidStr = (String) getRequestAttributes().get("dpid");
		Long dpid = HexString.toLong(dpidStr);
		String sliceStr = (String) getRequestAttributes().get("slice");
		String value = (String) getRequestAttributes().get("status");
		
		boolean status;
		if(value.equalsIgnoreCase("true")){
			status = true;
		}else{
			status = false;
		}

		boolean success = iFSFs.setSliceAdminState(dpid,sliceStr,status);
		return success;
	}
	
	
}
