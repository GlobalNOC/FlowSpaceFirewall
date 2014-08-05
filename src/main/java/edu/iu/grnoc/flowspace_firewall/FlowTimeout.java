package edu.iu.grnoc.flowspace_firewall;

import java.sql.Timestamp;

import org.openflow.protocol.OFFlowMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowTimeout {

	private OFFlowMod flow;
	private Timestamp expires;
	//to track idle timeout
	private int timeout;
	//is a hard timeout or an idle timeout?
	private boolean hard;
	private long packetCount;
	private static final Logger log = LoggerFactory.getLogger(FlowTimeout.class);
	
	public FlowTimeout(OFFlowMod flow, int timeout, boolean hard){
		this.flow = flow;
		this.hard = hard;
		
		this.expires = new Timestamp( System.currentTimeMillis() + (timeout  * 1000) );
		log.error("I expire at: " + this.expires.toString());
		this.timeout = timeout;
		this.packetCount = 0;
	}
	
	public OFFlowMod getFlow(){
		return this.flow;		
	}
	
	public boolean isExpired(){
		Timestamp now = new Timestamp(System.currentTimeMillis());
		log.error("Now: " + now.toString());
		log.error("Checking to see if I am expired");
		log.error("Expires: " + this.expires.toString());
		if(this.expires.before(now)){
			log.error("I am expired!!");
			return true;
		}else{
			log.error("I am not expired");
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
