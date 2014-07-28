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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VLANSlicer implements the slicer class
 * it determines if a flowMod or PacketIn event
 * is allowed for the given slice based on Port/VLAN tag
 * combinations
 * @author aragusa
 */

public class VLANSlicer implements Slicer{

	private HashMap<String, PortConfig> portList;
	private InetSocketAddress controllerAddress;
	private IOFSwitch sw;
	private RateTracker myRateTracker;
	private int maxFlows;
	private String name;
	private int packetInRate;
	private Map<Integer, byte[]> bufferIds;
	private boolean adminState;
	
	private static final Logger log = LoggerFactory.getLogger(VLANSlicer.class);
	
	public VLANSlicer(HashMap <String, PortConfig> ports, 
			InetSocketAddress controllerAddress, int rate, String name){
		myRateTracker = new RateTracker(10,100);
		portList = ports;
		this.name = name;
		this.adminState = true;
		
		if(controllerAddress == null){
			//not allowed
		}
		
		this.controllerAddress = controllerAddress;
		this.bufferIds = Collections.synchronizedMap(
				new LinkedHashMap<Integer,byte[]>(){
					/**
					 * 
					 */
					private static final long serialVersionUID = 1L;

					@Override
					public boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest)  
					{
						//when to remove the eldest entry
						return size() >= 1000 ;   //size exceeded the max allowed
					}
				}
				);
	}
	
	public VLANSlicer(){
		myRateTracker = new RateTracker(10,100);
		packetInRate = 10;
		portList = new HashMap<String,PortConfig>();
		name = "";
		this.adminState = true;
		this.bufferIds = Collections.synchronizedMap(
				new LinkedHashMap<Integer,byte[]>(){
					/**
					 * 
					 */
					private static final long serialVersionUID = 1L;

					@Override
					public boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest)  
					{
						//when to remove the eldest entry
						return size() >= 1000 ;   //size exceeded the max allowed
					}
				}
				);
	}
	
	public boolean getAdminState(){
		return this.adminState;
	}
	
	public void setPacketInRate(int rate){
		this.packetInRate = rate;
	}
	
	
	public void setPortId(String portName, short portId){
		
		PortConfig ptCnfg = this.getPortConfig(portName);
		if(ptCnfg != null){
			ptCnfg.setPortId(portId);
			log.debug("Set port: " + portName + " to port id: " + portId);
		}else{
			log.debug("NO configuration for port named: " + portName);
		}
	}
	
	/**
	 * sets the switch object as our slicer
	 * probably existed before the switch connected
	 * @param sw the IOFSwitch to set as the switch
	 */
	
	public void setSwitch(IOFSwitch sw){
		this.sw = sw;
		Iterator <ImmutablePort> portIterator = sw.getPorts().iterator();
		while(portIterator.hasNext()){
			ImmutablePort port = portIterator.next();
			PortConfig ptCfg = this.getPortConfig(port.getName());
			if(ptCfg != null){
				log.debug("Setting port named: " + port.getName() + " to port ID: " + port.getPortNumber());
				ptCfg.setPortId(port.getPortNumber());
			}else{
				log.debug("No configuration for port named: " + port.getName());
			}
		}
	}
	
	public void setAdminState(boolean state){
		this.adminState = state;
	}
	
	/**
	 * sets the portConfig for the portNamed portname
	 * @param portName
	 * @param portConfig
	 */
	
	public void setPortConfig(String portName, PortConfig portConfig){
		portList.put(portName, portConfig);
		if(this.sw != null){
			Iterator <ImmutablePort> portIterator = sw.getPorts().iterator();
			while(portIterator.hasNext()){
				ImmutablePort port = portIterator.next();
				log.debug("port: " + port.getName() + ":" + port.getPortNumber());
				if(port.getName().equals(portName)){
					PortConfig ptCfg = this.getPortConfig(port.getName());
					ptCfg.setPortId(port.getPortNumber());
					log.debug("Set port " + portConfig.getPortName() + " to port id " + port.getPortNumber());
				}
			}
		}
	}
	
	/**
	 * sets the maxFlows for the switch
	 * @param numberOfFlows
	 */
	
	public void setMaxFlows(int numberOfFlows){
		this.maxFlows = numberOfFlows;
	}
	
	public int getMaxFlows(){
		return this.maxFlows;
	}
	
	/**
	 * sets the flowRate for the switch/slice instance
	 * @param flowRate
	 */
	public void setFlowRate(int flowRate){
		this.myRateTracker.setRate(flowRate);
	}
	
	/**
	 * takes a number of flows and returns true if the number
	 * is greater than the max number of flow and false if it is not
	 * @param numberOfFlows
	 */
	
	public boolean isGreaterThanMaxFlows(int numberOfFlows){
		log.debug("Current Number of flows: " + numberOfFlows + " and maximum number of flows: " + this.maxFlows);
		if(numberOfFlows > this.maxFlows) {
			log.debug("Not at max number of flows");
			return true;
		}
		
		return false;
	}
	
	/**
	 * returns the <PortConfig> object for a given port
	 * based on the portId specified where portId is the 
	 * openflow identifier for the port
	 * If the switch is null (not connected) or the port is 
	 * not found, the return result will be null
	 * @param portId the openflow port id
	 **/
	
	public PortConfig getPortConfig(short portId){
		
		if(this.sw == null){
			throw new IllegalStateException("Switch not connected so we don't know the port id");
		}
		ImmutablePort thisPort = this.sw.getPort(portId);
		
		if(thisPort == null){
			log.warn("Port: " + portId + " was not found on this switch... sorry");
			return null;
		}
		log.debug("Looking for PORT: " + thisPort.getName());
		if(portList.containsKey(thisPort.getName())){
			return portList.get(thisPort.getName());
		}else{
			return null;
		}
	}
	
	/**
	 * returns the <PortConfig> object for a given port
	 * based on the portName if the port is 
	 * not found, the return result will be null
	 * @param portName the openflow name of the port
	 **/
	
	public PortConfig getPortConfig(String portName){
		return portList.get(portName);
	}
	
	/**
	 * expands the actions in a flowMod so that if we have an ALL
	 * action it will be output to all ports but the port it came frome
	 * @param flowMod
	 * @return
	 */
	
	private OFFlowMod expandActions(OFFlowMod flowMod){
		//quick note... if we are here then we have already expanded
		//the flow from wildcarded portId to individual portIds
		//ie.. this is an ok assumption to make
		Short portId = flowMod.getMatch().getInputPort();
		
		//get the list of actions
		List<OFAction> actions = flowMod.getActions();
		
		if(actions == null || actions.isEmpty()){
			log.debug("OFFlowMod actions are empty");
			return flowMod;
		}
		
		List<OFAction> newActions = new ArrayList<OFAction>();
		
		for(OFAction action : actions){
			log.debug("Checking Action Type: " + action.getType().name());
			
			if(action.getType() == OFActionType.OUTPUT){
				OFActionOutput output = (OFActionOutput) action;
				if(output.getPort() == OFPort.OFPP_ALL.getValue()){
					log.debug("Expanding Action OUTPUT: ALL");
					//first remove the current flow from the flowMods list
					//loop through all interfaces we have access to 
					//and add them to the list
					
					for(Map.Entry<String, PortConfig> port : this.portList.entrySet()){
						//ALL should forward out all interfaces except the one the packet came from
						if(port.getValue().getPortId() != portId){
							OFActionOutput newAct;
							try {
								newAct = (OFActionOutput) action.clone();
							} catch (CloneNotSupportedException e) {
								// TODO Auto-generated catch block
								//log.error(e.printStackTrace());
								log.error("Unable to clone the output action");
								log.error(e.getMessage());
								return null;
							}
							
							newAct.setPort(port.getValue().getPortId());
							newActions.add(newAct);
						}
					}
				}else if(output.getPort() == OFPort.OFPP_FLOOD.getValue()){
					return null;
				}else{
					//nothing to do here
					log.debug("Not an output of type all or flood");
					newActions.add(action);
				}
			}else{
				log.debug("Not an OUTPUT action");
				newActions.add(action);			
			}
		}
		
		OFFlowMod newFlow;
		try {
			newFlow = flowMod.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		newFlow.setActions(newActions);
		return newFlow;
		
	}
	
	private OFPacketOut clonePacketOut(OFPacketOut packet){
		OFPacketOut newOut = new OFPacketOut();
		newOut.setActions(packet.getActions());
		newOut.setPacketData(packet.getPacketData().clone());
		newOut.setBufferId(packet.getBufferId());
		newOut.setInPort(packet.getInPort());
		newOut.setLength(packet.getLength());
		newOut.setType(packet.getType());
		newOut.setXid(packet.getXid());
		return newOut;
	}
	
	public int getMaxFlowRate(){
		return this.myRateTracker.getMaxRate();
	}
	
	/**
	 * process an OFPacketOut message to verify that it fits
	 * in this slice properly.  We don't want one slice to
	 * be able to inject traffic into another slice erroneously
	 * @param output the OFPacketOut message to be properly sliced
	 */
	
	public List<OFMessage> allowedPacketOut(OFPacketOut outPacket){
		List <OFAction> newActions = new ArrayList<OFAction>();
		List <OFAction> actions = outPacket.getActions();
		List <OFMessage> packets = new ArrayList<OFMessage>();
		Iterator <OFAction> it = actions.iterator();
		OFMatch match = new OFMatch();
		if(outPacket.getPacketData().length == 0 && outPacket.getBufferId() != 0){
			//look at the buffer id and see if it matches one we have in our 
			//buffer cache
			int bufferId = outPacket.getBufferId();
			if(this.bufferIds.containsKey(bufferId)){
				outPacket.setBufferId(OFPacketOut.BUFFER_ID_NONE);
				outPacket.setPacketData(this.bufferIds.get(bufferId));
				outPacket.setLengthU(outPacket.getLengthU() + this.bufferIds.get(bufferId).length);
			}else{
				return packets;
			}
		}
	
		try{
			match.loadFromPacket(outPacket.getPacketData(),(short)0);
		}
		catch(Exception e){
			log.error("Loading Match from packet failed: " + e.getMessage());
			packets.clear();
			return packets;
		}
		//start our current vlan
		short curVlan = match.getDataLayerVirtualLan();
		while(it.hasNext()){
			OFAction action = it.next();
			//loop through the actions
			switch(action.getType()){
				case SET_VLAN_ID:
					//if its a set vlan then change our current vlan
					OFActionVirtualLanIdentifier setvid = (OFActionVirtualLanIdentifier)action;
					curVlan = setvid.getVirtualLanIdentifier();
					newActions.add(action);
					break;
				case OUTPUT:
					//if its an output, verify that the 
					OFActionOutput output = (OFActionOutput)action;
					if(output.getPort() == OFPort.OFPP_ALL.getValue()){
						log.info("output to ALL expanding");

						
						for(Map.Entry<String, PortConfig> port : this.portList.entrySet()){
							PortConfig myPortCfg = this.getPortConfig(port.getValue().getPortId());
							if(myPortCfg == null){
								log.info("output packet disallowed to port:" + port.getValue().getPortId());
								packets.clear();
								return packets;
							}
							if(!myPortCfg.vlanAllowed(curVlan)){
								log.info("Output packet disallowed for port:" + port.getValue().getPortId() + " and vlan: " + curVlan);
								packets.clear();
								return packets;
							}
							log.debug("Complicated case of OUTPUT ALL adding for port " + port.getValue().getPortId());
							List<OFAction> actualActions = new ArrayList<OFAction>();
							actualActions.addAll(newActions);
							OFPacketOut newOut = this.clonePacketOut(outPacket);
							OFActionOutput newOutput = new OFActionOutput();
							newOutput.setMaxLength(Short.MAX_VALUE);
							newOutput.setType(OFActionType.OUTPUT);
							newOutput.setLength((short)OFActionOutput.MINIMUM_LENGTH);
							newOutput.setPort(port.getValue().getPortId());
							actualActions.add(newOutput);
							newOut.setActions(actualActions);
							int size = 0;
							for(OFAction act : actualActions){
								size = size + act.getLengthU();
							}
							newOut.setActionsLength((short)size);
							packets.add(newOut);
						}
						
					}else if(output.getPort() == OFPort.OFPP_FLOOD.getValue()){
						log.info("output to flood not supported");
						packets.clear();
						return packets;
					}else{
						PortConfig myPortCfg = this.getPortConfig(output.getPort());
						if(myPortCfg == null){
							log.info("output packet disallowed to port:" + output.getPort());
							packets.clear();
							return packets;
						}
				
						//only return false if we fail
						//need to continue looping through on true case
						if(!myPortCfg.vlanAllowed(curVlan)){
							log.info("Output packet disallowed for port:" + output.getPort() + " and vlan: " + curVlan);
							packets.clear();
							return packets;
						}
						
						log.debug("Simple case, single output and it was allowed");
						List<OFAction> actualActions = new ArrayList<OFAction>();
						actualActions.addAll(newActions);
						OFPacketOut newOut = this.clonePacketOut(outPacket);
						actualActions.add(output);
						newOut.setActions(actualActions);
						int size = 0;
						for(OFAction act : actualActions){
							size = size + act.getLengthU();
						}
						newOut.setActionsLength((short)size);
						
						packets.add(newOut);
					}
					break;
				case STRIP_VLAN:
					curVlan = 0;
					newActions.add(action);
					break;
				default:
					newActions.add(action);
					break;
			}
		}
		log.debug("OutPackets: " + packets.toString());
		return packets;
	}
	
	/**
	 * Takes a flowMod and determines if it can be sent to the switch based
	 * on the policy.  If it can then
	 * @param flowMod OFFlowMod to be sliced and possibly exploded
	 * into multiple flow mods
	 */
	
	public List <OFFlowMod> allowedFlows(OFFlowMod flowMod){
		log.debug("Attempting to slice: " + flowMod.toString());
		List <OFFlowMod> flowMods = new ArrayList<OFFlowMod>();
		OFMatch match = flowMod.getMatch();
		
		if(match == null){
			return flowMods;
		}
		
		//if you wildcarded the vlan then tough we are blowing up now
		//we require an input vlan
		if(match.getDataLayerVirtualLan() == 0){
			log.debug("Slice: " + this.getSliceName() + ":" + this.sw.getStringId() + " Flow rule VLAN wildcarded.  Denied: " + flowMod.toString());
			return flowMods;
		}
		
		//wildcarded input port?
		if(match.getInputPort() == 0){
			//needs to expand it out unless has access to all ports
			Iterator<Entry<String, PortConfig>> it = this.portList.entrySet().iterator();
					
			while(it.hasNext()){
				//wildcarded port... so we need to loop through all the ports
				Map.Entry<String, PortConfig> port = (Entry<String, PortConfig>) it.next();
				if(port.getValue().getPortId() != 0){
					//create a new match like our old match but change the port
					log.debug("Expanding Match to port : " + port.getValue().getPortId());
					
					try{
						//why might this not be cloneable?  ahh... might not be clonable if there is no action!!
						OFFlowMod newFlow = flowMod.clone();
						//override the match with our new one
						newFlow.getMatch().setInputPort(port.getValue().getPortId());
						newFlow.getMatch().setWildcards(newFlow.getMatch().getWildcardObj().matchOn(Flag.IN_PORT));
						//allowed or not?
						log.debug("Attempting to verify expansion is allowed for port: " + newFlow.getMatch().getInputPort());
						//now to check and see if we need to expand because of output actions!
						log.debug("Attempting to expand actions");
						//expand actions
						OFFlowMod expandedFlow = this.expandActions(newFlow);
						
						if(expandedFlow == null){
							log.warn("Error exapnding actions for flow:" + newFlow.toString());
							flowMods.clear();
							return flowMods;
						}
						
						if(this.isFlowAllowed(expandedFlow)){
							flowMods.add(expandedFlow);
						}else{
							log.debug("denied Flow " + expandedFlow.toString());
							flowMods.clear();
							return flowMods;
						}
					}catch (CloneNotSupportedException e){
						log.error("This can't happen in the real world");
					}catch (Exception e){
						log.error("WTF");
					}
				}
			}
			
			//a quick optimization
			//if the number of flowMods = the number of ports on the switch
			//original flow mod is good
			log.debug("comparing number of flowMods to number of interfaces " + flowMods.size() + ":" + sw.getPorts().size());
			if(flowMods.size() == sw.getPorts().size()){
				log.debug("Number of flow rules matches the number if interfaces... original flow is good!");
				flowMods.clear();
				flowMods.add(flowMod);
			}
		}else{
			//no expansion necessary
			log.debug("No Match Expansion");
			
			log.debug("Attempting to expand actions");
			//expand actions
			OFFlowMod expandedFlow = this.expandActions(flowMod);
			
			if(expandedFlow == null){
				log.warn("Error expanding actions for flow:" + flowMod.toString());
				flowMods.clear();
				return flowMods;
			}
			
			if(this.isFlowAllowed(expandedFlow)){
				flowMods.add(expandedFlow);
			}else{
				log.debug("Denied flow " + expandedFlow.toString());
				flowMods.clear();
				return flowMods;
			}
		}
		log.debug("FLows: " + flowMods.toString());
		return flowMods;
	}
	
	/**
	 * Process a flowMod and determines if it is properly in the slice
	 * If it does match then the flow is returned.  If it does not
	 * directly match (ie... wildcarded port) then we expand
	 * and return an array of OFFlowMods to represent the expanded rule
	 * @param flowMod the openflow flow mode to be sliced
	 * 
	 */
	
	private Boolean isFlowAllowed(OFFlowMod flowMod){

		log.debug("helper slicing: " + flowMod.toString());
		OFMatch match = flowMod.getMatch();
		
		//we require an input port
		//need to do something special if you do have access to all ports
		if(match.getInputPort() == 0){
			//this is bad we shouldn't get here...
			log.debug("got a null port and we shouldn't have that");
			return false;
		}
		
		//we require an input vlan
		if(match.getDataLayerVirtualLan() == 0){
			log.debug("VLAN is wildcarded");
			return false;
		}
		
		if(this.sw == null){		
			log.debug("Switch is not defined");
			return false;
		}
		
		//get the port config
		PortConfig portCfg = this.getPortConfig(match.getInputPort());
		
		if(portCfg == null){
			//no port config we don't have access
			log.debug("port config not defined for port: " + match.getInputPort());
			return false;
		}
		
		//verify the match is allowed... if not bail
		if(portCfg.vlanAllowed(match.getDataLayerVirtualLan())){
			//need to iterate through the list of actions and make sure all
			//actions are allowed
			List <OFAction> actions = flowMod.getActions();
			if(actions == null){
				return true;
			}
			Iterator <OFAction> actionIterator = actions.iterator();
			//track our current vlan... start with the matchVlan
			short curVlan = match.getDataLayerVirtualLan();
			
			while(actionIterator.hasNext()){
				OFAction action = actionIterator.next();
				switch(action.getType()){
					//if we are setting the VLAN_ID
					case SET_VLAN_ID:
						OFActionVirtualLanIdentifier setvid = (OFActionVirtualLanIdentifier)action;
						curVlan = setvid.getVirtualLanIdentifier();
						break;
					//check to see if our current vlan is allowed
					//on this output port
					case OUTPUT:
						OFActionOutput output = (OFActionOutput)action;
						if(output.getPort() == OFPort.OFPP_CONTROLLER.getValue()){
							log.debug("output is to controller");
							break;
						}
						if(output.getPort() == OFPort.OFPP_ALL.getValue() || output.getPort() == OFPort.OFPP_FLOOD.getValue()){
							//should have been expanded before it got here
							return false;
						}
						
						PortConfig myPortCfg = this.getPortConfig(output.getPort());
						if(myPortCfg == null){
							log.debug("no port config found for port:" + output.getPort());
							return false;
						}
						//only return false if we fail
						//need to continue looping through on true case
						if(!myPortCfg.vlanAllowed(curVlan)){
							log.warn("FlowMod not allowed for port: " + output.getPort() + " and vlan: " + curVlan);
							return false;
						}
						break;
					//vlan required!
					case STRIP_VLAN:
						log.info("Doing a strip vlan");
						return false;
					//we are not slicing anything else at this time
					//so by default we let it through
					default:
						break;
				}
			}
			
			//woohoo our rule is allowed
			return true;
		}
		//nope... not letting it through
		log.debug("Policy for port: " + portCfg.getPortId() +":" + portCfg.getPortName() + " for vlan " + match.getDataLayerVirtualLan() + " said no");
		return false;
	}
	
	/**
	 * 
	 */
	public boolean isPortPartOfSlice(String portName){
		return portList.containsKey(portName);
	}
	
	/**
	 * 
	 */
	public boolean isPortPartOfSlice(short portId){
		if(this.sw == null){
			throw new IllegalStateException("Switch not connected so we don't know the port id");
		}
		
		ImmutablePort thisPort = this.sw.getPort(portId);
		if(thisPort == null){
			return false;
		}
		
		return portList.containsKey(thisPort.getName());
	}
	
	/**
	 * sets the controller address
	 * @param addr (InetSocketAddress) that represets the
	 * address to connect to 
	 */
	@Override
	public void setController(InetSocketAddress addr) {
		this.controllerAddress = addr;
	}

	/**
	 * returns the InetSocketAddress representing the address
	 * we will use/have used to connect to the controller
	 */
	
	@Override
	public InetSocketAddress getControllerAddress() {
		return this.controllerAddress;
	}


	@Override
	public boolean isOkToProcessMessage() {
		if(myRateTracker.okToProcess()){
			return true;
		}else{
			return false;
		}
	}
	
	public double getRate(){
		return myRateTracker.getRate();		
	}
	
	@Override
	public String getSliceName(){
		return this.name;
	}
	
	@Override
	public void setSliceName(String name){
		this.name = name;
	}
	
	public boolean hasOverlap(Slicer otherSlicer){
		for(String portName : this.portList.keySet()){
			if(otherSlicer.isPortPartOfSlice(portName)){
				if(this.getPortConfig(portName).getVlanRange().rangeOverlap(otherSlicer.getPortConfig(portName).getVlanRange())){
					log.error(""+this.name+" "+this.getSliceName()+"port range: "+this.getPortConfig(portName).getVlanRange().toString()+ "overlaps with slice: "+otherSlicer.getSliceName()+" port-range: "+otherSlicer.getPortConfig(portName).getVlanRange().toString());
					return true;
				}
			}
		}
		return false;
	}

	public IOFSwitch getSwitch(){
		return this.sw;
	}

	@Override
	public int getPacketInRate() {
		return this.packetInRate;
	}
	
	public void addBufferId(int bufferId, byte[] packetData){
		this.bufferIds.put(bufferId, packetData);
	}
}
