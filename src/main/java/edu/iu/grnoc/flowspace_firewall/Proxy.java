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

package edu.iu.grnoc.flowspace_firewall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Iterator;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.socket.*;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.*;

/**
 * Proxies all requests to and from the
 * switch and controller.  
 * @author aragusa
 *
 */

public class Proxy {

	private IOFSwitch mySwitch;
	private SocketChannel myController;
	private Slicer mySlicer;
	private OFControllerChannelHandler ofcch;
	private FlowSpaceFirewall parent;
	private XidMap xidMap;
	
	
	private static final Logger log = LoggerFactory.getLogger(Proxy.class);
	private Integer flowCount;
	private Boolean adminStatus;
	private RateTracker packetInRate;
	
	public Proxy(IOFSwitch switchImp, Slicer slicer, FlowSpaceFirewall fsf){
		mySlicer = slicer;
		mySwitch = switchImp;
		mySlicer.setSwitch(mySwitch);
		parent = fsf;
		flowCount = 0;
		xidMap = new XidMap();
		adminStatus = mySlicer.getAdminState();
		packetInRate = new RateTracker(100,slicer.getPacketInRate());

	}
	
	public void setAdminStatus(Boolean status){
		this.adminStatus = status;
		this.mySlicer.setAdminState(status);
		if(status){
			log.warn("Slice: "+ this.mySlicer.getSliceName() +" is re-enabled");
		}else{

			log.error("Disabling Slice:"+this.mySlicer.getSliceName() );
			if(this.connected()){
				this.removeFlows();
				this.disconnect();
			}
		}
	}
	
	public boolean getAdminStatus(){
		return this.adminStatus;
	}
	
	public double getPacketInRate(){
		return this.packetInRate.getRate();
	}
	
	public void removeFlows(){
		List<OFStatistics> results = this.parent.getSlicedFlowStats(mySwitch.getId(), this.mySlicer.getSliceName());
		
		if(results == null){
			log.debug("Slicing failed!");
			return;
		}
		
		List<OFMessage> deletes = new ArrayList<OFMessage>();
		
		for(OFStatistics stat : results){
			OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) stat;
			OFFlowMod flow = new OFFlowMod();
			flow.setMatch(flowStat.getMatch());
			flow.setActions(flowStat.getActions());
			int length = 0;
			for(OFAction act: flow.getActions()){
				length += act.getLength();
			}
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + length);
			flow.setCommand(OFFlowMod.OFPFC_DELETE);
			deletes.add(flow);
		}
		try {
			this.mySwitch.write(deletes, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public IOFSwitch getSwitch(){
		return this.mySwitch;
	}
	
	/**
	 * connects to the new channel
	 * @param channel
	 */
	public void connect(SocketChannel channel){
		if(!this.adminStatus){
			return;
		}
		if(myController != null && myController.isConnected()){
			return;
		}
		myController = channel;
		ofcch =(OFControllerChannelHandler)myController.getPipeline()
				.getContext("handler").getHandler();
		ofcch.setSwitch(mySwitch);
		ofcch.setProxy(this);
		myController.connect(mySlicer.getControllerAddress());
	}


	/**
	 * returns the current connected status of the proxy
	 * @return
	 */
	public boolean connected(){
		if(myController == null){
			return false;
		}
		ofcch =(OFControllerChannelHandler)myController.getPipeline()
				.getContext("handler").getHandler();
		if(!ofcch.isHandshakeComplete()){
			return false;
		}
		return myController.isConnected();
	}
	
	
	public Slicer getSlicer(){
		return this.mySlicer;
	}
	
	/**
	 * sets the slicer if for example the config is updated
	 * @param newSlicer
	 */
	public void setSlicer(Slicer newSlicer){
		if(!newSlicer.getControllerAddress().equals(this.mySlicer.getControllerAddress())){
			this.disconnect();
			//the controller connector will connect this to the proper address next time it runs
		}else{
			//since we aren't disconnecting/reconnecting we need to diff
			//the ports involved and synthetically generate port add/remove messages
			//to the controller to notify of the "change"
			Iterator <ImmutablePort> portIterator = this.mySwitch.getPorts().iterator();
			while(portIterator.hasNext()){
				ImmutablePort port = portIterator.next();
				PortConfig ptCfg = this.mySlicer.getPortConfig(port.getName());
				PortConfig ptCfg2 = newSlicer.getPortConfig(port.getName());
				if(ptCfg != null && ptCfg2 != null){
					//do nothing
				}else if(ptCfg == null && ptCfg2 != null){
					//port add!
					OFPortStatus portStatus = new OFPortStatus();
					portStatus.setDesc(this.mySwitch.getPort(port.getName()).toOFPhysicalPort());
					//boo OFPortReason.OFPPR_ADD is not a byte and has no toByte method :(
					portStatus.setReason((byte)0);
					
					//can't call toController because it isn't part of this slice yet!!!
					try {
						ofcch.sendMessage(portStatus);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else if(ptCfg != null && ptCfg2 == null){
					//port remove
					OFPortStatus portStatus = new OFPortStatus();
					portStatus.setDesc(this.mySwitch.getPort(port.getName()).toOFPhysicalPort());
					//boo OFPortReason.OFPPR_DELETE is not a byte and has no toByte method :(
					portStatus.setReason((byte)1);
					toController(portStatus,null);
				}else{
					//do nothing again
				}
			}
		}
		
		
		this.mySlicer = newSlicer;
		this.mySlicer.setSwitch(this.mySwitch);
		this.packetInRate.setRate(this.getSlicer().getPacketInRate());
	}
	
	/**
	 * disconnect from the controller
	 */
	public void disconnect(){
		if(myController.isConnected()){
			myController.disconnect();
		}
	}
	
	private void mapXids(List <OFMessage> msg){
		Iterator <OFMessage> it = msg.iterator();
		while(it.hasNext()){
			OFMessage message = it.next();
			mapXids(message);
		}
		
	}
	
	private void mapXids(OFMessage msg){
		
		int switchId = this.mySwitch.getNextTransactionId();
		int controllerId = msg.getXid();
		xidMap.put(switchId, controllerId);
		msg.setXid(switchId);
		
	}
	
	private synchronized void updateFlowCount(int change){
		this.flowCount += change;
	}
	
	public int getFlowCount(){
		return this.flowCount;
	}
	
	/**
	 * send an error back with a matching Xid to the controller
	 * @param msg
	 */
	private void sendError(OFMessage msg){
		OFError error = new OFError();
		error.setErrorType(OFError.OFErrorType.OFPET_BAD_REQUEST);
		error.setErrorCode(OFError.OFBadRequestCode.OFPBRC_EPERM);
		error.setXid(msg.getXid());
		error.setOffendingMsg(msg);
		//don't need to set the size... it knows how to do it!
		//error.setLengthU( msg.getLengthU() + 12);
		
		try {
			ofcch.sendMessage(error);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void processFlowMod(OFMessage msg, FloodlightContext cntx){
		List <OFFlowMod> flows = this.mySlicer.allowedFlows((OFFlowMod)msg);
		if(flows.size() == 0){
			//really we need to send a perm error
			log.info("Flow is not allowed");
			this.sendError((OFMessage)msg);
			return;
		}else{
			log.info("Flow is allowed");
		}
		List <OFMessage> messages = new ArrayList<OFMessage>();
		//count the total number of flowMods
		Iterator <OFFlowMod> it = flows.iterator();
		while(it.hasNext()){
			OFFlowMod flow = it.next();
			messages.add((OFMessage) msg);
			switch(flow.getCommand()){
			case OFFlowMod.OFPFC_ADD:
				if( this.mySlicer.isGreaterThanMaxFlows(this.flowCount + 1) ) {
					log.warn("Switch: "+this.mySwitch.getStringId()+" Slice: "+this.mySlicer.getSliceName()+" Flow count is already at threshold. Skipping flow mod");
					this.sendError((OFMessage)msg);
					return;
				}
				this.updateFlowCount(1);
				break;
			case OFFlowMod.OFPFF_CHECK_OVERLAP:
				if( this.mySlicer.isGreaterThanMaxFlows(this.flowCount + 1) ) {
					log.warn("Switch: "+this.mySwitch.getStringId()+" Slice: "+this.mySlicer.getSliceName()+"Flow count is already at threshold. Skipping flow mod");
					this.sendError((OFMessage)msg);
					return;
				}
				this.updateFlowCount(1);
				break;
			case OFFlowMod.OFPFC_DELETE:
				this.updateFlowCount(-1);
				break;
			case OFFlowMod.OFPFC_DELETE_STRICT:
				this.updateFlowCount(-1);
				break;
			case OFFlowMod.OFPFC_MODIFY:
				//++ and -- so noop
				break;
			}
		}
		
		mapXids(messages);
		try {
			mySwitch.write(messages, cntx);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mySwitch.flush();
	}
	
	private void handleStatsRequest(OFMessage msg){
		OFStatisticsRequest request = (OFStatisticsRequest) msg;
		switch(request.getStatisticType()){
		case FLOW:
			handleFlowStatsRequest(msg);
			return;
		case DESC:
			handleDescrStatsRequest(msg);
			return;
		case VENDOR:
			handleVendorStatsRequest(msg);
			return;
		case AGGREGATE:
			handleAggregateStatsRequest(msg);
			return;
		case TABLE:
			handleTableStatsRequest(msg);
			return;
		case PORT:
			handlePortStatsRequest(msg);
			return;
		case QUEUE:
			handleQueueStatsRequest(msg);
			return;
		}
		
	}
	
	private void handleAggregateStatsRequest(OFMessage msg){
		OFStatisticsRequest request = (OFStatisticsRequest) msg;
		
		OFAggregateStatisticsRequest specificRequest = (OFAggregateStatisticsRequest) request.getFirstStatistics();
		
		List<OFStatistics> stats = this.parent.getStats(mySwitch.getId());
		List<OFStatistics> results = null;
		try{
			results = FlowStatSlicer.SliceStats(mySlicer, stats);
		}catch(IllegalArgumentException e){
			
		}
		
		if(results == null){
			log.debug("Slicing failed!");
			return;
		}
		
		//TODO: write a match comparison to see if flow stats match fits this requested match
		//OFMatch match = specificRequest.getMatch();
		short port = specificRequest.getOutPort();
		
		long packet_count = 0;
		long byte_count = 0;
		int flow_count = 0;
		
		Iterator<OFStatistics> it = results.iterator();
		while(it.hasNext()){
			OFFlowStatisticsReply stat = (OFFlowStatisticsReply) it.next();
			//does this meet our match/out port before we include this
			//TODO: write a match comparison to see if the flow stats match fits in the requests match
			if(true){
				//ok so our match doesn't work... do we have the out_port set
				if(port != OFPort.OFPP_NONE.getValue()){
					//ok so we should look for the output port
					List<OFAction> actions = stat.getActions();
					Iterator<OFAction> actIt = actions.iterator();
					while(actIt.hasNext()){
						OFAction act = actIt.next();
						if(act.getType() == OFActionType.OUTPUT){
							OFActionOutput outAct = (OFActionOutput) act;
							if(outAct.getPort() == port){
								//this one meets all of our requirements
								packet_count += stat.getPacketCount();
								byte_count += stat.getByteCount();
								flow_count += 1;
							}
						}
					}
				}else{
					//we are not comparing the out port so just add
					packet_count += stat.getPacketCount();
					byte_count += stat.getByteCount();
					flow_count += 1;
				}
			}
		}
		
		//generate the reply and send it
		OFAggregateStatisticsReply stat = new OFAggregateStatisticsReply();
		stat.setByteCount(byte_count);
		stat.setPacketCount(packet_count);
		stat.setFlowCount(flow_count);
		OFStatisticsReply reply = new OFStatisticsReply();
		reply.setStatisticType(OFStatisticsType.AGGREGATE);
		List<OFStatistics> statsReply = new ArrayList<OFStatistics>();
		statsReply.add(stat);
		reply.setStatistics(stats);
		reply.setXid(msg.getXid());
		reply.setFlags((short)0x0000);
		try {
			ofcch.sendMessage(reply);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return;
		
	}
	
	//TODO: implement this
	private void handleTableStatsRequest(OFMessage msg){
		
	}
	
	private void handlePortStatsRequest(OFMessage msg){
		OFStatisticsRequest request = (OFStatisticsRequest) msg;
		OFPortStatisticsRequest specificRequest = (OFPortStatisticsRequest) request.getFirstStatistics();
		
		List<OFStatistics> statsReply = new ArrayList<OFStatistics>();
		int length = 0;
		
		if(specificRequest.getPortNumber() != OFPort.OFPP_NONE.getValue()){
			OFStatistics myStat = this.parent.getPortStats(mySwitch.getId(), specificRequest.getPortNumber());
			statsReply.add(myStat);
			length += myStat.getLength();
			
		}else{
			HashMap<Short, OFStatistics> allPortStats = this.parent.getPortStats(mySwitch.getId());
			Iterator<Entry<Short, OFStatistics>> it = allPortStats.entrySet().iterator();
			while(it.hasNext()){
				Entry<Short, OFStatistics> entry = it.next();
				length += entry.getValue().getLength();
				statsReply.add(entry.getValue());
			}
		}
		OFStatisticsReply reply = new OFStatisticsReply();
		reply.setStatisticType(OFStatisticsType.PORT);
		reply.setStatistics(statsReply);
		reply.setXid(msg.getXid());
		reply.setFlags((short)0x0000);
		reply.setLengthU(length + reply.getLength());
		try {
			ofcch.sendMessage(reply);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return;
	}
	
	//TODO: implement this
	private void handleVendorStatsRequest(OFMessage msg){
		
		return;
	}
	
	private void handleQueueStatsRequest(OFMessage msg){
		return;
	}
	
	private void handleDescrStatsRequest(OFMessage msg){
		OFDescriptionStatistics descrStats = mySwitch.getDescriptionStatistics();
		OFStatisticsReply reply = new OFStatisticsReply();
		reply.setStatisticType(OFStatisticsType.DESC);
		List<OFStatistics> stats = new ArrayList<OFStatistics>();
		stats.add(descrStats);
		reply.setStatistics(stats);
		reply.setXid(msg.getXid());
		reply.setFlags((short)0x0000);
		reply.setLengthU(descrStats.getLength() + reply.getLength());
		try {
			ofcch.sendMessage(reply);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return;
	}
		
	
	private void handleFlowStatsRequest(OFMessage msg){
		//we have the stats cached so slice n' dice and return
		log.debug("Working on stats for switch: " + mySwitch.getStringId() + " for slice this slice");
		List<OFStatistics> results = this.parent.getSlicedFlowStats(mySwitch.getId(),this.mySlicer.getSliceName());
		
		if(results == null){
			log.debug("Slicing failed!");
			return;
		}

		log.debug("Sliced Stats: " + results.toString());

		/**
		 * When flow-stats are sliced we should update the total number of flows.
		 * Some rules might get errors or replace others, and tracking that is very difficult. 
		 * So every time we slice the stats we should set the actual number of flows from that slice.
		*/
		this.flowCount = results.size();		
		
		//if no results... just short circuit
		if(results.size() <= 0){
			OFStatisticsReply reply = new OFStatisticsReply();
			reply.setStatistics(results);
			reply.setStatisticType(OFStatisticsType.FLOW);
			reply.setXid(msg.getXid());
			reply.setFlags((short)0x0000);
			try {
				ofcch.sendMessage(reply);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		log.info("About to turn flowstats into reply");
		short length = 0;
		int counter = 0;
		Iterator <OFStatistics> it2 = results.iterator();				
		List<OFFlowStatisticsReply> limitedResults = new ArrayList<OFFlowStatisticsReply>();
		
		while(it2.hasNext()){
			//TODO: implement the filter capabilities of FLowStats Request
			OFFlowStatisticsReply stat = (OFFlowStatisticsReply) it2.next();
			length += stat.getLength();
			counter++;
			
			limitedResults.add(stat);
			
			if(counter >= 10 || it2.hasNext() == false){
				OFStatisticsReply reply = new OFStatisticsReply();
				reply.setStatistics(limitedResults);
				reply.setStatisticType(OFStatisticsType.FLOW);
				reply.setXid(msg.getXid());
				
				if(it2.hasNext() == false){
					reply.setFlags((short)0x0000);
				}else{
					reply.setFlags((short)0x0001);
				}
				
				length += reply.getLength();
				reply.setLength(length);
				try {
					ofcch.sendMessage(reply);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
				counter = 0;
				length = 0;
				limitedResults = new ArrayList<OFFlowStatisticsReply>();
			}	
		}
		
	}
	
	/**
	 * slice requests from the controller to the switch.
	 * if a message isn't allowed then return with the sendError
	 * @param msg
	 * @param cntx
	 */
	public void toSwitch(OFMessage msg, FloodlightContext cntx){
		//first figure out what the message is
		log.debug("Proxy Slicing request of type: " + msg.getType());
		if(!this.mySlicer.isOkToProcessMessage()){
			log.warn("Switch: "+this.mySwitch.getStringId()+"Slice:"+this.mySlicer.getSliceName()+"Rate limit exceeded");
			this.sendError((OFMessage)msg);
			return;
		}
		
		switch(msg.getType()){
			case FLOW_MOD:
				processFlowMod(msg, cntx);
				return;
			case PACKET_OUT:
				//super simple case no need for the extra method
				List<OFMessage> allowed = this.mySlicer.allowedPacketOut((OFPacketOut)msg);
				if(allowed.isEmpty()){
					//really we need to send a perm error
					log.info("PacketOut is not allowed");
					this.sendError((OFMessage)msg);
					return;
				}else{
					log.info("PacketOut is allowed");
					mapXids(allowed);
					try {
						mySwitch.write(allowed, cntx);
					} catch (IOException e) {
						e.printStackTrace();
					}
					mySwitch.flush();
				}
				return;
			case STATS_REQUEST:
				handleStatsRequest(msg);
				return;
			case PORT_MOD:
				this.sendError((OFMessage)msg);
				return;
			case SET_CONFIG:
				this.sendError((OFMessage)msg);
				return;
			default:
				//do nothing.. basically fall through to the write
				break;
		}

		
		mapXids(msg);

		if(!this.valid_header(msg)){
			//invalid packet don't send it back so we cant send an error
			//just log and drop it
			log.error("Slice " + this.getSlicer().getSliceName() + " to switch " + this.mySwitch.getStringId() + "  Invalid Header Rejecting!");
			return;
		}
		
		try {
			mySwitch.write(msg, cntx);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mySwitch.flush();
		
	}
	
	/**
	 * 
	 */
	
	public boolean valid_header(OFMessage msg){
		//verify the length is what it claims
		ChannelBuffer buf = ChannelBuffers.buffer(msg.getLength());
		try{
			msg.writeTo(buf);
		}catch(Exception e){
			return false;
		}
		int size = buf.readableBytes();
		if(size != msg.getLength())
			return false;
		
		return true;
	}
	
	/**
	 * handle messages from the switch and verify they should be a part of this slice
	 * if not just return
	 * @param msg
	 * @param cntx
	 */
	public void toController(OFMessage msg, FloodlightContext cntx){
		if(ofcch == null){
			return;
		}
		
		log.debug("Proxy Handling message of type: " + msg.getType());
		int xid = msg.getXid();
		switch(msg.getType()){
		case PACKET_IN:
			OFPacketIn pcktIn = (OFPacketIn) msg;
			OFMatch match = new OFMatch();
			if(pcktIn.getPacketData().length <= 0){
				log.debug("No Packet data not slicing");
				//no packet not slicing...
				return;
			}
			match.loadFromPacket(pcktIn.getPacketData(),pcktIn.getInPort());
			OFFlowMod flowMod = new OFFlowMod();
			flowMod.setMatch(match);
			List <OFFlowMod> allowed = this.mySlicer.allowedFlows(flowMod);
			if(allowed.size() == 0){
				log.debug("Packet in Not allowed for slice: "+this.mySlicer.getSliceName());
				return;
			}
			
			if(this.packetInRate.okToProcess()){
				//add the packet buffer id to our buffer id list
				this.mySlicer.addBufferId(pcktIn.getBufferId(), pcktIn.getPacketData());
				break;
			}else{
				log.error("Packet in Rate for Slice: " +
			this.getSlicer().getSliceName() + ":" + this.getSwitch().getStringId() +
			" has passed the packet in rate limit Disabling slice!!!!");
				this.setAdminStatus(false);
				return;
			}
			
		case PORT_STATUS:
			//only send port status messages
			//for interfaces involved with this slice
			OFPortStatus portStatus = (OFPortStatus)msg;
			OFPhysicalPort port = portStatus.getDesc();
			if(!this.mySlicer.isPortPartOfSlice(port.getName())){
				log.debug("Port status even for switch"+this.mySwitch.getStringId()+" port " + port.getName() + " is not allowed for slice"+this.mySlicer.getSliceName());
				return;
			}
			
			
			switch(OFPortReason.fromReasonCode(portStatus.getReason())){
			case OFPPR_ADD:
				this.mySlicer.setPortId(port.getName(), port.getPortNumber());
				break;
			case OFPPR_MODIFY:
				//nothing to do here
				break;
			case OFPPR_DELETE:
				//nothing to do here
				break;
			}
			
			break;
		case ERROR:
			if(xidMap.containsKey(xid)){
				msg.setXid(xidMap.get(xid));
				xidMap.remove(xid);
			}else{
				return;
			}
			break;
		case FLOW_REMOVED:
			OFFlowRemoved removedFlow = (OFFlowRemoved) msg;
			OFFlowMod mod = new OFFlowMod();
			mod.setMatch(removedFlow.getMatch());
			List <OFFlowMod> flows = mySlicer.allowedFlows(mod);
			if(flows.size() == 0){
				return;
			}
			break;
		case BARRIER_REPLY:
			if(xidMap.containsKey(xid)){	
				msg.setXid(xidMap.get(xid));	
				//ISSUE=7276 delete all keys up to and including the barrier, but not any new xids that have come in since the barrier request
				 xidMap.removeToKey(xid);
			}else{
				return;
			}
			break;
		case ECHO_REQUEST:
			return;
		default:
			//not slicing it
			log.debug("Not Slicing message of Type: " + msg.getType());
			break;
		}
		//we made it this far so send the message
		try {
			ofcch.sendMessage(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

	