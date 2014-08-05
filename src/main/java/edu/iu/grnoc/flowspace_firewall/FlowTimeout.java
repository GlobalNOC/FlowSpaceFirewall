package edu.iu.grnoc.flowspace_firewall;

import java.sql.Timestamp;

import org.openflow.protocol.OFFlowMod;

public class FlowTimeout {

	private OFFlowMod flow;
	private Timestamp expires;
	//to track idle timeout
	private int timeout;
	//is a hard timeout or an idle timeout?
	private boolean hard;
	private long packetCount;
	
	
	public FlowTimeout(OFFlowMod flow, int timeout, boolean hard){
		this.flow = flow;
		this.hard = hard;
		this.expires = new Timestamp( timeout );
		this.timeout = timeout;
		this.packetCount = 0;
	}
	
	public OFFlowMod getFlow(){
		return this.flow;		
	}
	
	public boolean isExpired(){
		Timestamp now = new Timestamp(System.currentTimeMillis());
		if(this.expires.after(now)){
			return true;
		}else{
			return false;
		}
	}

	public boolean isHard(){
		return this.hard;
	}
	
	public void setPacketCount(long pktCount){
		this.packetCount = pktCount;
	}
	
	public long getPacketCount(){
		return this.packetCount;
	}
	
	public void updateLastUsed(){
		this.expires = new Timestamp(System.currentTimeMillis() + (timeout * 1000));
	}
	
}
