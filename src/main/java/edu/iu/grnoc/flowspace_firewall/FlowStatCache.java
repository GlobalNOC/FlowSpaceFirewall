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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.Wildcards;

/**
 * Stores the stats for all of the switches
 * for quick retreiveal without overloading
 * the switches.  The Synchronized methods give
 * essentially a mutex for accessing the data
 * @author aragusa
 *
 */

public class FlowStatCache{

	//the logger
	private static final Logger log = LoggerFactory.getLogger(FlowStatCache.class);
	//the cache
	private HashMap<Long, List<OFStatistics>> flowStats;
	private HashMap<Long, HashMap<Short, OFStatistics>> portStats;
	private HashMap<Long, HashMap<String, List<OFStatistics>>> sliced;
	private HashMap<Long, HashMap<OFMatch, OFStatistics>> map;
	

	private FlowSpaceFirewall parent;
	
	public FlowStatCache(FlowSpaceFirewall parent){
		//this is the raw flowStats from the switch
		flowStats = new HashMap<Long, List<OFStatistics>>();
		//this is the raw portStat from the switch
		portStats = new HashMap<Long, HashMap<Short, OFStatistics>>();
		//this is the mapping from DPID OFMatch to FlowMod
		map = new HashMap<Long, HashMap<OFMatch, OFStatistics>>();
		//this is the results to be returned when requested
		sliced = new HashMap<Long, HashMap<String, List<OFStatistics>>>();
		//need one more to track the lastSeen time
		this.parent = parent;
	}
	
	
	//lets us write out object to disk
	public synchronized void writeObject(ObjectOutputStream aOutputStream) throws IOException{
		//need to clone it so that we can make changes while serializing
		aOutputStream.writeObject(sliced.clone());
	}
	
	//lets us read our object from disk
	@SuppressWarnings("unchecked")
	public void readObject(ObjectInputStream aInputStream) throws IOException{
		HashMap<Long, HashMap<String, List<OFStatistics>>> cache;
		try {
			cache = (HashMap<Long, HashMap<String, List<OFStatistics>>>) aInputStream.readObject();
			this.sliced = cache;

			long time = System.currentTimeMillis();
			for(long dpid : this.sliced.keySet()){
				HashMap<String, List<OFStatistics>> sliceMap = this.sliced.get(dpid);
				for(String sliceName : sliceMap.keySet()){
					List<OFStatistics> stats = sliceMap.get(sliceName);
					for(OFStatistics stat: stats){
						FSFWOFFlowStatisticsReply flowStat = (FSFWOFFlowStatisticsReply)stat;
						flowStat.setLastSeen(time);
					}
				}
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			log.error("Error reading in cache file!  Starting from clean cache!");
			e.printStackTrace();
		}
	}
	
	public synchronized void delFlowMod(long dpid, String sliceName, OFFlowMod flow){
		FSFWOFFlowStatisticsReply flowStat = (FSFWOFFlowStatisticsReply) this.findCachedStat(dpid, flow.getMatch(), sliceName);
		if(flowStat != null){
			log.debug("Setting flow mod to be deleted");
			flowStat.setToBeDeleted(true);
			return;
		}
		log.info("Flow mod was not found and could not be deleted");
	}
	
	public synchronized void addFlowMod(Long dpid, String sliceName, OFFlowMod flow){
		//create a flow stat reply and set the cache to it
		FSFWOFFlowStatisticsReply flowStat = new FSFWOFFlowStatisticsReply();
		flowStat.setMatch(flow.getMatch());
		flowStat.setActions(flow.getActions());
		flowStat.setPacketCount(0);
		flowStat.setByteCount(0);
		flowStat.setPriority(flow.getPriority());
		flowStat.setCookie(flow.getCookie());
		flowStat.setHardTimeout(flow.getHardTimeout());
		flowStat.setIdleTimeout(flow.getIdleTimeout());
		short length = 0;
		for(OFAction act : flowStat.getActions()){
			length += act.getLengthU();
		}
		flowStat.setLength((short)(OFFlowStatisticsReply.MINIMUM_LENGTH + length));

		if(sliced.containsKey(dpid)){
			HashMap<String, List<OFStatistics>> sliceStats = sliced.get(dpid);
			if(sliceStats.containsKey(sliceName)){
				log.debug("Adding Flow to the cache!");
				sliceStats.get(sliceName).add(flowStat);
				log.debug("sliced stats size: " + sliceStats.get(sliceName).size());
			}else{
				List<OFStatistics> stats = new ArrayList<OFStatistics>();
				log.debug("Adding flow to the cache! Created the Slice hash");
				stats.add(flowStat);		
				sliceStats.put(sliceName, stats);
				
			}
		}else{
			HashMap<String, List<OFStatistics>> sliceStats = new HashMap<String, List<OFStatistics>>();
			List<OFStatistics> stats = new ArrayList<OFStatistics>();
			sliceStats.put(sliceName, stats);
			stats.add(flowStat);
			log.debug("Adding flow to the cache, switch didn't exist");
			sliced.put(dpid, sliceStats);
		}
		//need to update last seen
		log.debug("Added Flow: " + flowStat.toString() + " to cache!");
		flowStat.setLastSeen(System.currentTimeMillis());
	}	
	
	public List <IOFSwitch> getSwitches(){
		return this.parent.getSwitches();
	}
	
	
	
	public synchronized void clearFlowCache(Long switchId){
		flowStats.remove(switchId);
	}
	
	/**
	 * just updates the data in the flowstat
	 * @param cachedStat
	 * @param newStat
	 */
	
	private boolean updateFlowStatData(OFStatistics cachedStat, OFFlowStatisticsReply newStat){
		FSFWOFFlowStatisticsReply cachedFlowStat = (FSFWOFFlowStatisticsReply) cachedStat;
		if(cachedFlowStat.toBeDeleted()){
			return false;
		}
		cachedFlowStat.setByteCount(cachedFlowStat.getByteCount() + newStat.getByteCount());
		cachedFlowStat.setPacketCount(cachedFlowStat.getPacketCount() + newStat.getPacketCount());
		cachedFlowStat.setDurationNanoseconds(newStat.getDurationNanoseconds());
		cachedFlowStat.setDurationSeconds(newStat.getDurationSeconds());
		cachedFlowStat.setLastSeen(System.currentTimeMillis());
		cachedFlowStat.setVerified(true);
		return true;
	}
	
	/**
	 * internally used to find a cachedStat based on a match, only 
	 * @param switchId
	 * @param match
	 * @return
	 */
	
	private FSFWOFFlowStatisticsReply findCachedStat(Long switchId, OFMatch match){
		log.debug("looking for stat in our expected cache: " + match.toString());
		if(sliced.containsKey(switchId)){
			for(String slice: sliced.get(switchId).keySet()){
				List <OFStatistics> expectedStats = new ArrayList<OFStatistics>(sliced.get(switchId).get(slice));
				for(OFStatistics expectedOFStat: expectedStats){
					FSFWOFFlowStatisticsReply expectedFlowStat = (FSFWOFFlowStatisticsReply) expectedOFStat;
					log.debug("Comparing to match: " + expectedFlowStat.getMatch());
					if(expectedFlowStat.getMatch().equals(match)){
						//found it
						log.debug("found the expected flow match!");
						if(expectedFlowStat.toBeDeleted()){
							continue;
						}else{
							return expectedFlowStat;
						}
					}
				}
			}
		}
		log.debug("Nothing matching that match!!");
		return null;
	}
	
	
	private FSFWOFFlowStatisticsReply findCachedStat(Long switchId, OFMatch match, String sliceName){
		log.debug("looking for stat in our expected cache: " + match.toString());
		if(sliced.containsKey(switchId)){
			if(sliced.get(switchId).containsKey(sliceName)){
				List <OFStatistics> expectedStats = new ArrayList<OFStatistics>(sliced.get(switchId).get(sliceName));
				for(OFStatistics expectedOFStat: expectedStats){
					FSFWOFFlowStatisticsReply expectedFlowStat = (FSFWOFFlowStatisticsReply) expectedOFStat;
					
					if(expectedFlowStat.getMatch().equals(match)){
						//found it
						log.debug("found the expected flow match!");
						return expectedFlowStat;
					}
				}
			}
		}
		log.debug("Nothing matching that match!!");
		return null;
	}
	
	private OFFlowMod buildFlowMod(OFFlowStatisticsReply flowStat){
		OFFlowMod flowMod = new OFFlowMod();
		flowMod.setMatch(flowStat.getMatch().clone());
		flowMod.setActions(flowStat.getActions());
		flowMod.setPriority(flowStat.getPriority());
		flowMod.setCookie(flowStat.getCookie());
		flowMod.setIdleTimeout(flowStat.getIdleTimeout());
		flowMod.setHardTimeout(flowStat.getHardTimeout());
		return flowMod;
	}
	
	
	private Slicer findSliceForFlow(long switchId, OFFlowMod flowMod){
		List<HashMap<Long, Slicer>> slices = parent.getSlices();
		for(HashMap<Long,Slicer> tmpSlices : slices){
			if(!tmpSlices.containsKey(switchId)){
				//switch not part of this slice
				log.debug("Switch is not part of this slice!");
				continue;
			}
	
			Slicer slice = tmpSlices.get(switchId);
			log.debug("Looking at slice: " + slice.getSliceName());
			List<OFFlowMod> flows = slice.allowedFlows(flowMod);
			if(flows.size() > 0){
				return slice;
			}
		}
		return null;
	}
	
	private void processFlow(Long switchId, OFFlowStatisticsReply flowStat, long time){
		
		if(!map.containsKey(switchId)){
			HashMap<OFMatch, OFStatistics> tmpMap = new HashMap<OFMatch, OFStatistics>();
			map.put(switchId, tmpMap);
		}
		
		HashMap<OFMatch, OFStatistics> flowMap = map.get(switchId);
		
		if(flowMap.containsKey(flowStat.getMatch())){
			log.debug("Found the flow rule in our mapping");
			if(this.updateFlowStatData(flowMap.get(flowStat.getMatch()), flowStat)){
				return;
			}else{
				//uh oh this was set to be deleted...
				log.debug("I just tried to update a flow I thought was deleted!!!");
			}
		}
		log.debug("didn't find the flow rule in our mapping must be new");
		//the flow mapping wasn't found... so now we must try a few things
		//first does it match any flow we were expecting?
		FSFWOFFlowStatisticsReply stat = this.findCachedStat(switchId, flowStat.getMatch());
		if(stat == null){
			log.debug("flow stat was not in our expected, trying by wildcarding IN_PORT");
			//ok so we didn't find it first go around
			//wildcard the in_port and try again
			OFMatch match = flowStat.getMatch().clone();
			match.setInputPort((short)0);
			match.setWildcards(match.getWildcardObj().wildcard(Wildcards.Flag.IN_PORT));
			stat = this.findCachedStat(switchId, match);
		}
		
		if(stat == null){
			log.debug("still haven't found it, but we will keep trying");
			//ok... haven't found either... managed tag mode?
			//figure out what slice it is a part of
			OFFlowMod flowMod = this.buildFlowMod(flowStat);
			Slicer slice = this.findSliceForFlow(switchId, flowMod);
			if(slice != null){
				//one last thing... if we are in managed tag mode wildcard the vlan and now does it match
				if(slice.getTagManagement()){
					OFMatch match = flowStat.getMatch().clone();
					match.setDataLayerVirtualLan((short)0);
					match.setWildcards(match.getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN));
					stat = this.findCachedStat(switchId, match, slice.getSliceName());
					
				}
				
				if(stat == null){
					//ok still didn't match... now to wildcard both the VLAN and IN_PORT
					OFMatch match = flowStat.getMatch().clone();
					match.setDataLayerVirtualLan((short)0);
					match.setWildcards(match.getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN));
					match.setInputPort((short)0);
					match.setWildcards(match.getWildcardObj().wildcard(Wildcards.Flag.IN_PORT));
					stat = this.findCachedStat(switchId, match, slice.getSliceName());
				}
				
				if(stat == null){
					log.debug("Switch: " + switchId + ", Unable to find a flow that matches this flow in my cache, adding it");
					log.debug(flowStat.toString());
					if(slice.getTagManagement()){
						OFMatch match = flowStat.getMatch().clone();
						match.setDataLayerVirtualLan((short)0);
						match.setWildcards(match.getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN));
						flowMod.setMatch(match);
						this.addFlowMod(switchId, slice.getSliceName(), flowMod);
						stat = this.findCachedStat(switchId,  flowMod.getMatch());
					}else{
						this.addFlowMod(switchId, slice.getSliceName(), flowMod);
						stat = this.findCachedStat(switchId,  flowMod.getMatch());
					}
				}
			}
		}
			
		//by this point we have either found or added our stat to the mappings
		//just update it 
		if(stat != null){
			log.debug("Updating Flow Stat");
			flowMap.put(flowStat.getMatch().clone(), (OFStatistics)stat);
			log.debug("Map size: " + flowMap.size());
			this.updateFlowStatData(stat, flowStat);
		}else{ 
			log.error("Error finding/adding flow stat to the cache!  This flow is not a part of any Slice!" + flowStat.toString());
			//remove flow
			List<IOFSwitch> switches = getSwitches();
			for(IOFSwitch sw : switches){
				if(sw.getId() == switchId){
					OFFlowMod flowMod = new OFFlowMod();
					flowMod.setMatch(flowStat.getMatch().clone());
					flowMod.setIdleTimeout(flowStat.getIdleTimeout());
					flowMod.setHardTimeout(flowStat.getHardTimeout());
					flowMod.setCookie(flowStat.getCookie());
					flowMod.setPriority(flowStat.getPriority());
					flowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
					flowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH);
					flowMod.setXid(sw.getNextTransactionId());

					List<OFMessage> msgs = new ArrayList<OFMessage>();
					msgs.add((OFMessage)flowMod);
					
					try {
						sw.write(msgs, null);
					} catch (IOException e) {
						log.error("Error attempting to send flow delete for flow that fits in NO flowspace");
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * setFlowCache
	 * sets the stats for the given switch
	 * @param switchId
	 * @param stats
	 */
	public synchronized void setFlowCache(Long switchId, List <OFStatistics> stats){
		flowStats.put(switchId, stats);
		log.debug("Setting Flow Cache! Switch: " + switchId + " Total Stats: " + stats.size());
		
		//first thing is to set all counters for all stats to 0
		if(this.sliced.containsKey(switchId)){
			//loop through our current cache and set all packet/byte counts to 0
			Iterator<String> it = this.sliced.get(switchId).keySet().iterator();
			while(it.hasNext()){
				String slice = (String)it.next();
				List<OFStatistics> ofStats = this.sliced.get(switchId).get(slice);
				for(OFStatistics stat : ofStats){
					OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) stat;
					flowStat.setByteCount(0);
					flowStat.setPacketCount(0);
				}
			}
		}
		
		
		//now update process all the flows find their mapping and cache them
		long time = System.currentTimeMillis();
		//loop through all stats
		for(OFStatistics stat : stats){
			OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) stat;
			log.debug("Processing Flow: " + flowStat.toString());
			this.processFlow(switchId, flowStat, time);
		}
		
		//are there any flows that need to go away (ie... we didn't see them since the last poll cycle)		
		long timeToRemove = time - 60000;
		if(this.sliced.containsKey(switchId)){
			HashMap<String, List<OFStatistics>> sliceStats = this.sliced.get(switchId);
			Iterator<String> it = sliceStats.keySet().iterator();
			while(it.hasNext()){
				String slice = (String)it.next();
				List<OFStatistics> ofStats = this.sliced.get(switchId).get(slice);
				Iterator<OFStatistics> itStat = ofStats.iterator();
				while(itStat.hasNext()){
					OFStatistics stat = (OFStatistics)itStat.next();
					FSFWOFFlowStatisticsReply flowStat = (FSFWOFFlowStatisticsReply)stat;
					if(flowStat.lastSeen() < timeToRemove){
						log.debug("Removing flowStat: " + stat.toString());
						itStat.remove();
							//have to also find all flows that point to this flow :(
						this.removeMappedCache(switchId, flowStat);
					}
				}
			}
		}
	}
	
	/**
	 * removeMappedCache
	 * @param switchId
	 * @param stat
	 * 
	 * removes the flows that are mapped to this stats
	 * 
	 */
	
	private void removeMappedCache(long switchId, OFStatistics stat){
		if(map.containsKey(switchId)){
			HashMap<OFMatch, OFStatistics> switchMap = map.get(switchId);
			if(switchMap.containsValue(stat)){
				//well crap no easy way to do this...
				Iterator<Entry<OFMatch, OFStatistics>> it = switchMap.entrySet().iterator();
				while(it.hasNext()){
					Entry<OFMatch, OFStatistics> entry = (Entry<OFMatch, OFStatistics>) it.next();
					if(entry.getValue().equals(stat)){
						it.remove();
					}
				}
			}
			
		}
	}
	
	/**
	 * tries to find expired flows and then signals that they need to be removed!
	 * @param switchId
	 * @return
	 */
	
	public List<FlowTimeout> getPossibleExpiredFlows(Long switchId){
		List<FlowTimeout> flowTimeouts = new ArrayList<FlowTimeout>();
		List<HashMap<Long, Slicer>> slices = parent.getSlices();

		for(HashMap<Long,Slicer> tmpSlices : slices){
			if(!tmpSlices.containsKey(switchId)){
				//switch not part of this slice
				continue;
			}
			Proxy proxy = this.parent.getProxy(switchId, tmpSlices.get(switchId).getSliceName());
			if(proxy == null){
				return flowTimeouts;
			}
			flowTimeouts.addAll( proxy.getTimeouts());
		}
			
		return flowTimeouts;
	}
	
	/**
	 * check for expired flows
	 * @param switchId
	 */
	
	public void checkExpireFlows(Long switchId){
		List<HashMap<Long, Slicer>> slices = parent.getSlices();

		for(HashMap<Long,Slicer> tmpSlices : slices){
			if(!tmpSlices.containsKey(switchId)){
				//switch not part of this slice
				continue;
			}
			Proxy p = this.parent.getProxy(switchId, tmpSlices.get(switchId).getSliceName());
			if(p == null){
				return;
			}
			p.checkExpiredFlows();
		}
	}
	
	
	/**
	 * retrieves the stats for the requested switch
	 * @param switchId
	 * @return
	 */
	public synchronized List <OFStatistics> getSwitchFlowStats(Long switchId){
		log.debug("Looking for switch stats: " + switchId);
		return flowStats.get(switchId);
	}
	
	public synchronized List <OFStatistics> getSlicedFlowStats(Long switchId, String sliceName){
		log.debug("Getting sliced stats for switch: " + switchId + " and slice " + sliceName);
		if(!flowStats.containsKey(switchId)){
			return null;
		}
		if(sliced.containsKey(switchId)){
			HashMap<String, List<OFStatistics>> tmpStats = sliced.get(switchId);
			if(tmpStats.containsKey(sliceName)){				
				//create a copy of the array so we can manipulate it
				List<OFStatistics> stats = new ArrayList<OFStatistics>(tmpStats.get(sliceName));

				//we only want verified flows to appear
				Iterator<OFStatistics> it = stats.iterator();
				while(it.hasNext()){
					FSFWOFFlowStatisticsReply flowStat = (FSFWOFFlowStatisticsReply)it.next();
					if(flowStat.toBeDeleted() || !flowStat.isVerified()){
						it.remove();
					}
				}
				
				log.debug("Returning " + stats.size() + " flow stats");
				return stats;
			}
			log.debug("Switch cache has no slice cache named: " + sliceName);
			return new ArrayList<OFStatistics>();
		}
		log.debug("Switch cache does not even exist");
		return new ArrayList<OFStatistics>();
	}
	
	
	public synchronized void setPortCache(Long switchId, HashMap<Short, OFStatistics> stats){
		portStats.put(switchId, stats);
	}
	
	public synchronized OFStatistics getPortStats(Long switchId, short portId){
		HashMap<Short, OFStatistics> nodeStats = portStats.get(switchId);
		return nodeStats.get(portId);
	}
	
	public synchronized HashMap<Short, OFStatistics> getPortStats(Long switchId){
		return portStats.get(switchId);
	}
	
	
}
