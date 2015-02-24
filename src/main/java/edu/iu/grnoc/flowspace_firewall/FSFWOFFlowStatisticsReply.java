package edu.iu.grnoc.flowspace_firewall;

import org.openflow.protocol.statistics.OFFlowStatisticsReply;

public class FSFWOFFlowStatisticsReply extends OFFlowStatisticsReply{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2332359404798187668L;
	private long lastSeen = 0;
	private boolean verified = false;
	private boolean flaggedForDelete = false;
	
	public boolean isVerified(){
		return verified;
	}

	public void setVerified(boolean ver){
		this.verified = ver;
	}
	
	public void setLastSeen(long time){
		lastSeen = time;
	}
	
	public long lastSeen(){
		return lastSeen;
	}
	
	public boolean toBeDeleted(){
		return flaggedForDelete;
	}
	
	public void setToBeDeleted(boolean status){
		flaggedForDelete = status;
	}
	
}
