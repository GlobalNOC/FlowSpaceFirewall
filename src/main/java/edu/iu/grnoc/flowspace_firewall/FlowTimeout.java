package edu.iu.grnoc.flowspace_firewall;

import java.sql.Timestamp;

import net.floodlightcontroller.core.FloodlightContext;

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
	private FloodlightContext context;
	
	private static final Logger log = LoggerFactory.getLogger(FlowTimeout.class);
	
	public FlowTimeout(OFFlowMod flow, int timeout, boolean hard, FloodlightContext context){
		this.flow = flow;
		this.hard = hard;
		this.context = context;
		this.expires = new Timestamp( System.currentTimeMillis() + (timeout  * 1000) );
		log.error("I expire at: " + this.expires.toString());
		this.timeout = timeout;
		this.packetCount = 0;
	}
	
	public OFFlowMod getFlow(){
		return this.flow;		
	}
	
	public FloodlightContext getContext(){
		return this.context;
	}
	
	public boolean isExpired(){
		Timestamp now = new Timestamp(System.currentTimeMillis());
		if(this.expires.before(now)){
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
