package edu.iu.grnoc.flowspace_firewall;

public class FlowSpaceFirewallParams {
	private int stats_poll_interval;
	
	public FlowSpaceFirewallParams(){
		this.stats_poll_interval = 10; // 10 seconds is the default polling interval.
	}

	public void setStatsPollInterval(int newInterval){
		this.stats_poll_interval = newInterval;
	}
	
	public int getStatsPollInterval(){
		return this.stats_poll_interval;
	}
}
