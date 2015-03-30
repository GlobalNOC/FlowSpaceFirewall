package edu.iu.grnoc.flowspace_firewall;

import java.util.List;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;

public class FSFWOFFlowStatisticsReply extends OFFlowStatisticsReply{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2332359404798187668L;
	private long lastSeen = 0;
	private boolean verified = false;
	private boolean flaggedForDelete = false;
	private String sliceName;
	private FSFWOFFlowStatisticsReply parentStat;
	private boolean hasParent = false;
	
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
	
	public void setSliceName(String slice){
		this.sliceName = slice;
	}
	
	public String getSliceName(){
		return this.sliceName;
	}
	
	public boolean hasParent(){
		return this.hasParent;
	}
	
	public void setParentStat(FSFWOFFlowStatisticsReply stat){
		this.hasParent = true;
		this.parentStat = stat;
	}
	
	public FSFWOFFlowStatisticsReply getParentStat(){
		return this.parentStat;
	}
	
	/*
	 * method to compare actions
	 */
	
	public boolean compareActions(List<OFAction> otherActs){
		//short circuit the sizes aren't the same... bail
		if(this.actions.size() != otherActs.size()){
			return false;
		}
		
		//compare each action verify they are the same
		for(int i=0;i<this.actions.size();i++){
			if(!this.actions.get(i).equals(otherActs.get(i))){
				//nope not the same
				return false;				
			}
		}
		
		//made it this far so they are the same!
		return true;
	}
	
}
