/*
 Copyright 2014 Trustees of Indiana University

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Iterator;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.socket.*;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.factory.MessageParseException;
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
import net.floodlightcontroller.packet.Ethernet;

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
	private List<FlowTimeout> timeouts;
		
	public Proxy(IOFSwitch switchImp, Slicer slicer, FlowSpaceFirewall fsf){
		mySlicer = slicer;
		mySwitch = switchImp;
		mySlicer.setSwitch(mySwitch);
		parent = fsf;
		flowCount = 0;
		xidMap = new XidMap();
		adminStatus = mySlicer.getAdminState();
		packetInRate = new RateTracker(10000,slicer.getPacketInRate());
		timeouts = Collections.synchronizedList( new ArrayList<FlowTimeout>());
		
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
	
	public List<FlowTimeout> getTimeouts(){
		return this.timeouts;
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
			this.flowCount = this.flowCount - 1;
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
		if(myController == null){
			return;
		}
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
	
	public void setFlowCount(int totalFlows){
		log.debug("set flow count to: " + totalFlows + " for slice " + this.getSlicer().getSliceName() + " " + this.getSlicer().getSwitchName());
		this.flowCount = totalFlows;
	}
	
	/**
	 * send an error back with a matching Xid to the controller
	 * @param msg
	 */
	private void sendError(OFMessage msg, OFError error){
		error.setXid(msg.getXid());
		error.setOffendingMsg(msg);
	
		try {
			ofcch.sendMessage(error);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * check to see if any flows are expired
	 * this is called from the stats cacher after
	 * the stats cacher updates the stats
	 */
	
	public void checkExpiredFlows(){
		log.debug("Checking for expired flows");
		Iterator<FlowTimeout> it = this.timeouts.iterator();
		while(it.hasNext()){
			FlowTimeout timeout = it.next();
			if(timeout.isExpired()){
				log.debug("Removing Flow that has timed out");
				it.remove();
				OFFlowMod flow = timeout.getFlow();
				flow.setOutPort(OFPort.OFPP_NONE);
				flow.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
				flow.setHardTimeout((short)0);
				flow.setIdleTimeout((short)0);
				flow.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
				this.toSwitch((OFMessage) flow,  timeout.getContext());				
			}
		}
	}
	
	private void processFlowMod(OFMessage msg, FloodlightContext cntx){
		List <OFFlowMod> flows;
		
		OFFlowMod tmpFlow = (OFFlowMod)msg;
		if(tmpFlow.getCommand() == OFFlowMod.OFPFC_DELETE && tmpFlow.getMatch().equals(new OFMatch())){
			//this is a delete all flow path
			this.removeFlows();
			return;
		}
		
		if(this.mySlicer.getTagManagement()){
			flows = this.mySlicer.managedFlows(tmpFlow);
			if(flows.size() ==0){
				log.error("Slice: " + this.mySlicer.getSliceName() + ":" + this.getSlicer().getSwitchName() + " denied flow: " + ((OFFlowMod)msg).toString());
				OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
				error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
				this.sendError((OFMessage)msg,error );
				return;
			}else{
				log.info("Slice: " + this.mySlicer.getSliceName() + ":" + this.getSlicer().getSwitchName() + " Sent Flow: " + ((OFFlowMod)msg).toString());
			}
		}else{
			flows = this.mySlicer.allowedFlows(tmpFlow);
			if(flows.size() == 0){
				//really we need to send a perm error
				log.error("Slice: " + this.mySlicer.getSliceName() + ":" + this.getSlicer().getSwitchName() + " denied flow: " + ((OFFlowMod)msg).toString());
				OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
				error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
				this.sendError((OFMessage)msg,error);
				return;
			}else{
				log.info("Slice: " + this.mySlicer.getSliceName() + ":" + this.getSlicer().getSwitchName() + " Sent Flow: " + ((OFFlowMod)msg).toString());
			}
		}
		
		if(tmpFlow.getCommand() == OFFlowMod.OFPFC_ADD || tmpFlow.getCommand() == OFFlowMod.OFPFF_CHECK_OVERLAP 
				|| tmpFlow.getCommand() == OFFlowMod.OFPFC_MODIFY || tmpFlow.getCommand() == OFFlowMod.OFPFC_MODIFY_STRICT){
			this.parent.addFlowCache(this.mySwitch.getId(), this.mySlicer.getSliceName(), tmpFlow);
		}
		if(tmpFlow.getCommand() == OFFlowMod.OFPFC_DELETE || tmpFlow.getCommand() == OFFlowMod.OFPFC_DELETE_STRICT){
			this.parent.delFlowCache(this.mySwitch.getId(), this.mySlicer.getSliceName(), tmpFlow);
		}
		List <OFMessage> messages = new ArrayList<OFMessage>();
		//count the total number of flowMods
		Iterator <OFFlowMod> it = flows.iterator();
		while(it.hasNext()){
			OFFlowMod flow = it.next();
			
			switch(flow.getCommand()){
			case OFFlowMod.OFPFC_ADD:

				if( this.mySlicer.isGreaterThanMaxFlows(this.flowCount + 1) ) {
					log.warn("Switch: "+this.getSlicer().getSwitchName()+" Slice: "+this.mySlicer.getSliceName()+" Flow count is already at threshold. Skipping flow mod");
					OFError error = new OFError(OFError.OFErrorType.OFPET_FLOW_MOD_FAILED);
					error.setErrorCode(OFError.OFFlowModFailedCode.OFPFMFC_ALL_TABLES_FULL);
					this.sendError((OFMessage)msg, error);
					return;
				}
				//if this switch does not support idle/hard timeouts
				//we need to strip the idle/hard timeout from the flow mod 
				//and implement them in FSFW
				if(this.mySlicer.doTimeouts()){
					if(flow.getIdleTimeout() != 0){
						FlowTimeout timeout = new FlowTimeout(flow, flow.getIdleTimeout(), false, cntx);					
						this.timeouts.add(timeout);
						flow.setIdleTimeout((short)0);
					}
					if(flow.getHardTimeout() != 0){
						FlowTimeout timeout = new FlowTimeout(flow, flow.getHardTimeout(), true, cntx);					
						this.timeouts.add(timeout);
						flow.setHardTimeout((short)0);
					}
				}
				
				this.updateFlowCount(1);
				break;
			case OFFlowMod.OFPFF_CHECK_OVERLAP:

				if( this.mySlicer.isGreaterThanMaxFlows(this.flowCount + 1) ) {
					log.warn("Switch: "+this.getSlicer().getSwitchName()+" Slice: "+this.mySlicer.getSliceName()+"Flow count is already at threshold. Skipping flow mod");
					OFError error = new OFError(OFError.OFErrorType.OFPET_FLOW_MOD_FAILED);
					error.setErrorCode(OFError.OFFlowModFailedCode.OFPFMFC_ALL_TABLES_FULL);
					this.sendError((OFMessage)msg,error);
					return;
				}
				//if this switch does not support idle/hard timeouts
				//we need to strip the idle/hard timeout from the flow mod 
				//and implement them in FSFW
				if(this.mySlicer.doTimeouts()){
					if(flow.getIdleTimeout() != 0){
						FlowTimeout timeout = new FlowTimeout(flow, flow.getIdleTimeout(), false, cntx);					
						this.timeouts.add(timeout);
						flow.setIdleTimeout((short)0);
					}
					if(flow.getHardTimeout() != 0){
						FlowTimeout timeout = new FlowTimeout(flow, flow.getHardTimeout(), true, cntx);					
						this.timeouts.add(timeout);
						flow.setHardTimeout((short)0);
					}
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
			messages.add((OFMessage) flow);
		}
		log.error("Sending messages: " + messages.toString());		
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
		
		//List<OFStatistics> stats = this.parent.getStats(mySwitch.getId());
		List<OFStatistics> results = this.parent.getSlicedFlowStats(this.mySwitch.getId(), this.mySlicer.getSliceName());

		if(results == null){
			//slicing didn't fail we just haven't polled the switch yet
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
		reply.setStatistics(statsReply);
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
		log.debug("Working on stats for switch: " + this.getSlicer().getSwitchName() + " for slice this slice");
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
		short length = 0;
		int counter = 0;
		Iterator <OFStatistics> it2 = results.iterator();				
		List<OFFlowStatisticsReply> limitedResults = new ArrayList<OFFlowStatisticsReply>();
		
		while(it2.hasNext()){
			//TODO: implement the filter capabilities of FLowStats Request
			OFFlowStatisticsReply stat = (OFFlowStatisticsReply) it2.next();
			
			if(this.mySlicer.getTagManagement()){
				OFFlowStatisticsReply newStat = new OFFlowStatisticsReply();
				//in managed tag mode... remove the vlan tag from the match and any set vlan actions
				newStat.setMatch(stat.getMatch().clone());
				newStat.setPriority(stat.getPriority());
				newStat.setIdleTimeout(stat.getIdleTimeout());
				newStat.setHardTimeout(stat.getHardTimeout());
				newStat.setPacketCount(stat.getPacketCount());
				newStat.setByteCount(stat.getByteCount());
				newStat.setDurationNanoseconds(stat.getDurationNanoseconds());
				newStat.setDurationSeconds(stat.getDurationSeconds());
				newStat.setTableId(stat.getTableId());
				newStat.setCookie(stat.getCookie());
				newStat.getMatch().setDataLayerVirtualLan((short)0);
				newStat.getMatch().setWildcards(newStat.getMatch().getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN));
				List<OFAction> newActions = new ArrayList<OFAction>();
				short actLength = 0;
				List<OFAction> actions = stat.getActions();
				Iterator<OFAction> actIt = actions.iterator();
				while(actIt.hasNext()){
					OFAction act = actIt.next();
					switch(act.getType()){
					case SET_VLAN_ID:
						break;
					case SET_VLAN_PCP:
						break;
					case STRIP_VLAN:
						break;
					default:
						newActions.add(act);
						actLength += act.getLength();
						break;
					}
				}
				newStat.setActions(newActions);
				newStat.setLength((short)(OFFlowStatisticsReply.MINIMUM_LENGTH + actLength));
				stat = newStat;
			}
						
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
			log.warn("Switch: "+this.getSlicer().getSwitchName()+"Slice:"+this.mySlicer.getSliceName()+"Rate limit exceeded");
			OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
			error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
			this.sendError((OFMessage)msg,error);
			return;
		}
		
		switch(msg.getType()){
			case FLOW_MOD:
				processFlowMod(msg, cntx);
				return;
			case PACKET_OUT:
				//super simple case no need for the extra method
				if(this.mySlicer.getTagManagement()){
					List<OFMessage> allowed = this.mySlicer.managedPacketOut((OFPacketOut)msg);
					if(allowed.isEmpty()){
						//really we need to send a perm error
						log.info("PacketOut is not allowed");
						OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
						error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
						this.sendError((OFMessage)msg,error);
						return;
					}else{
						log.debug("PacketOut is allowed");
						mapXids(allowed);
						try {
							mySwitch.write(allowed, cntx);
						} catch (IOException e) {
							e.printStackTrace();
						}
						mySwitch.flush();
					}
				}else{
					List<OFMessage> allowed = this.mySlicer.allowedPacketOut((OFPacketOut)msg);
					if(allowed.isEmpty()){
						//really we need to send a perm error
						log.info("PacketOut is not allowed");
						OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
						error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
						this.sendError((OFMessage)msg,error);
						return;
					}else{
						log.debug("PacketOut is allowed");
						mapXids(allowed);
						try {
							mySwitch.write(allowed, cntx);
						} catch (IOException e) {
							e.printStackTrace();
						}
						mySwitch.flush();
					}
				}
				return;
			case STATS_REQUEST:
				handleStatsRequest(msg);
				return;
			case PORT_MOD:
				OFError error = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
				error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
				this.sendError((OFMessage)msg,error);
				return;
			case SET_CONFIG:
				OFError error2 = new OFError(OFError.OFErrorType.OFPET_BAD_REQUEST);
				error2.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
				this.sendError((OFMessage)msg,error2);
				return;
			default:
				//do nothing.. basically fall through to the write
				break;
		}

		
		mapXids(msg);

		if(!this.valid_header(msg)){
			//invalid packet don't send it back so we cant send an error
			//just log and drop it
			log.error("Slice " + this.getSlicer().getSliceName() + " to switch " + this.mySlicer.getSwitchName() + "  Invalid Header Rejecting!");
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
				//we add the packet with the vlan id on it but send a modified packet in to the controller
				//without the vlan tag
				if(this.mySlicer.getTagManagement()){
					log.debug("Processing Packet in for Managed Tag mode");
					Ethernet newPkt = new Ethernet();
					byte[] pktData = pcktIn.getPacketData();
					newPkt.deserialize(pktData,0,pktData.length);
					newPkt.setEtherType(newPkt.getEtherType());
					newPkt.setVlanID(Ethernet.VLAN_UNTAGGED);
					
					//Set the packet data based on the length of the serialize function's returned
					//value.  Do it this way because serialize() might remove a number of padding bytes,
					//so we cannot just assume the number of bytes removed will be 4.
					byte[] newPktData = newPkt.serialize();
					pcktIn.setPacketData(newPktData);
					pcktIn.setTotalLength((short) newPktData.length);
				}
				break;
			}else{
				log.error("Packet in Rate for Slice: " +
							this.getSlicer().getSliceName() + ":" + this.getSlicer().getSwitchName() +
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
				log.debug("Port status even for switch" + this.getSlicer().getSwitchName() + " port " + port.getName() + " is not allowed for slice"+this.mySlicer.getSliceName());
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
				OFError error = (OFError) msg;
				OFMessage error_msg = null;
				try{
					error_msg = error.getOffendingMsg();
				} catch (MessageParseException e) {
					// TODO Auto-generated catch block
					log.error("Unable to parse error's offending message");
					break;
				}
				if(error_msg == null){
					break;
				}
				switch(error_msg.getType()){
					case FLOW_MOD:
						OFFlowMod mod = (OFFlowMod) error_msg;
						switch(mod.getCommand()){
						case OFFlowMod.OFPFC_ADD:
							this.flowCount--;
							break;
						case OFFlowMod.OFPFC_DELETE:
							this.flowCount++;
							break;
						case OFFlowMod.OFPFC_DELETE_STRICT:
							this.flowCount++;
							break;
						default:
							break;
						}
						break;
					default:
						break;
					}

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
			
			if(mySlicer.getTagManagement()){
				removedFlow.getMatch().setDataLayerVirtualLan((short)0);
				removedFlow.getMatch().getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN);
				msg = removedFlow;
			}
			
			this.flowCount--;
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

	