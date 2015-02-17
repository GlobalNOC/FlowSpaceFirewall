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

import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
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
	private HashMap<Long, List<OFStatistics>> cache;
	private HashMap<Long, HashMap<Short, OFStatistics>> portStatsCache;
	private HashMap<Long, HashMap<String, List<OFStatistics>>> slicedCache;
	private HashMap<Long, HashMap<OFMatch, OFStatistics>> mappedCache;
	private HashMap<OFStatistics, Long> lastSeen;
	

	private FlowSpaceFirewall parent;
	
	public FlowStatCache(FlowSpaceFirewall parent){
		//this is the raw flowStats from the switch
		cache = new HashMap<Long, List<OFStatistics>>();
		//this is the raw portStat from the switch
		portStatsCache = new HashMap<Long, HashMap<Short, OFStatistics>>();
		//this is the mapping from DPID OFMatch to FlowMod
		mappedCache = new HashMap<Long, HashMap<OFMatch, OFStatistics>>();
		//this is the results to be returned when requested
		slicedCache = new HashMap<Long, HashMap<String, List<OFStatistics>>>();
		//need one more to track the lastSeen time
		lastSeen = new HashMap<OFStatistics, Long>();
		this.parent = parent;
	}
	
	
	//lets us write out object to disk
	public void writeObject(ObjectOutputStream aOutputStream) throws IOException{
		//need to clone it so that we can make changes while serializing
		aOutputStream.writeObject(slicedCache.clone());
	}
	
	//lets us read our object from disk
	@SuppressWarnings("unchecked")
	public void readObject(ObjectInputStream aInputStream) throws IOException{
		HashMap<Long, HashMap<String, List<OFStatistics>>> cache;
		try {
			cache = (HashMap<Long, HashMap<String, List<OFStatistics>>>) aInputStream.readObject();
			this.slicedCache = cache;
			//we need to set lastSeen for everything to be now!
			for(long dpid : this.slicedCache.keySet()){
				HashMap<String, List<OFStatistics>> sliceMap = this.slicedCache.get(dpid);
				for(String sliceName : sliceMap.keySet()){
					List<OFStatistics> stats = sliceMap.get(sliceName);
					for(OFStatistics stat: stats){
						lastSeen.put(stat, System.currentTimeMillis());
					}
				}
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void addFlowMod(Long dpid, String sliceName, OFFlowMod flow){
		//create a flow stat reply and set the cache to it
		OFFlowStatisticsReply flowStat = new OFFlowStatisticsReply();
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

		if(slicedCache.containsKey(dpid)){
			HashMap<String, List<OFStatistics>> sliceStats = slicedCache.get(dpid);
			if(sliceStats.containsKey(sliceName)){
				sliceStats.get(sliceName).add(flowStat);
			}else{
				List<OFStatistics> stats = new ArrayList<OFStatistics>();
				stats.add(flowStat);		
				sliceStats.put(sliceName, stats);
			}
		}else{
			HashMap<String, List<OFStatistics>> sliceStats = new HashMap<String, List<OFStatistics>>();
			List<OFStatistics> stats = new ArrayList<OFStatistics>();
			sliceStats.put(sliceName, stats);
			stats.add(flowStat);
			slicedCache.put(dpid, sliceStats);
		}
		lastSeen.put(flowStat, System.currentTimeMillis());
	}	
	
	public List <IOFSwitch> getSwitches(){
		return this.parent.getSwitches();
	}
	
	public synchronized void clearFlowCache(Long switchId){
		
		cache.remove(switchId);
		slicedCache.remove(switchId);
		
	}
	
	private void updateFlowStatData(OFStatistics cachedStat, OFFlowStatisticsReply newStat){
		OFFlowStatisticsReply cachedFlowStat = (OFFlowStatisticsReply) cachedStat;
		cachedFlowStat.setByteCount(cachedFlowStat.getByteCount() + newStat.getByteCount());
		cachedFlowStat.setPacketCount(cachedFlowStat.getPacketCount() + newStat.getPacketCount());
		lastSeen.put(cachedStat, System.currentTimeMillis());
	}
	
	
	private OFFlowStatisticsReply findCachedStat(Long switchId, OFMatch match){
		log.debug("looking for stat in our expected cache: " + match.toString());
		for(String slice: slicedCache.get(switchId).keySet()){
			List <OFStatistics> expectedStats = slicedCache.get(switchId).get(slice);
			for(OFStatistics expectedOFStat: expectedStats){
				OFFlowStatisticsReply expectedFlowStat = (OFFlowStatisticsReply) expectedOFStat;
				if(expectedFlowStat.getMatch().equals(match)){
					//found it
					return expectedFlowStat;
				}
			}
		}
		return null;
	}
	
	private void processFlow(Long switchId, OFFlowStatisticsReply flowStat){
		
		if(mappedCache.containsKey(switchId)){
			//switch exists does the curently mapped Flows have this flow
			HashMap<OFMatch, OFStatistics> sliceMap = mappedCache.get(switchId);
			if(sliceMap.containsKey(flowStat.getMatch())){
				log.debug("Found the flow rule in our mapping");
				this.updateFlowStatData(sliceMap.get(flowStat.getMatch()), flowStat);
			}else{
				log.debug("didn't find the flow rule in our mapping must be new");
				//the flow mapping wasn't found... so now we must try a few things
				//first does it match any flow we were expecting?
				OFFlowStatisticsReply stat = this.findCachedStat(switchId, flowStat.getMatch());
				if(stat == null){
					log.debug("flow stat was not in our expected, trying by wildcarding IN_PORT");
					//ok so we didn't find it first go around
					//wildcard the in_port and try again
					OFMatch match = flowStat.getMatch();
					match.setInputPort((short)0);
					match.getWildcardObj().wildcard(Wildcards.Flag.IN_PORT);
					stat = this.findCachedStat(switchId, match);
				}
				
				if(stat == null){
					//ok... haven't found either... managed tag mode?
					//figure out what slice it is a part of
					List<HashMap<Long, Slicer>> slices = parent.getSlices();

					for(HashMap<Long,Slicer> tmpSlices : slices){
						if(!tmpSlices.containsKey(switchId)){
							//switch not part of this slice
							continue;
						}
						if(!slicedCache.containsKey(switchId)){
							HashMap<String, List<OFStatistics>> tmp = new HashMap<String, List<OFStatistics>>();
							slicedCache.put(switchId, tmp);
						}
						Slicer slice = tmpSlices.get(switchId);
						OFFlowMod flowMod = new OFFlowMod();
						flowMod.setMatch(flowStat.getMatch());
						flowMod.setActions(flowStat.getActions());
						flowMod.setPriority(flowStat.getPriority());
						flowMod.setCookie(flowStat.getCookie());
						flowMod.setIdleTimeout(flowStat.getIdleTimeout());
						flowMod.setHardTimeout(flowStat.getHardTimeout());
						List<OFFlowMod> flows = slice.allowedFlows(flowMod);
						if(flows.size() > 0){
							if(slice.getTagManagement()){
								//ok so its probably in need of some wildcarding
								//first remove the vlan tag
								OFMatch match = flowStat.getMatch();
								match.setDataLayerVirtualLan((short)0);
								match.getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN);
								stat = this.findCachedStat(switchId, match);
								if(stat == null){
									//ok so maybe its been both expanded and tag managed
									match.setInputPort((short)0);
									match.getWildcardObj().wildcard(Wildcards.Flag.IN_PORT);
									stat = this.findCachedStat(switchId, match);
								}
							}
							if(stat == null){
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
					sliceMap.put(flowStat.getMatch(), stat);
					this.updateFlowStatData(stat, flowStat);
				}else{
					log.error("Error finding/adding flow stat to the cache!");
				}
			}
		}
	}
	
	/**
	 * sets the stats for the given switch
	 * @param switchId
	 * @param stats
	 */
	public synchronized void setFlowCache(Long switchId, List <OFStatistics> stats){
		cache.put(switchId, stats);
	
		if(this.slicedCache.containsKey(switchId)){
			//loop through our current cache and set all packet/byte counts to 0
			Iterator<String> it = this.slicedCache.get(switchId).keySet().iterator();
			while(it.hasNext()){
				String slice = (String)it.next();
				List<OFStatistics> ofStats = this.slicedCache.get(switchId).get(slice);
				for(OFStatistics stat : ofStats){
					OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) stat;
					flowStat.setByteCount(0);
					flowStat.setPacketCount(0);
				}
			}
		}
		
		//loop through all stats
		for(OFStatistics stat : stats){
			OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) stat;
			this.processFlow(switchId, flowStat);
		}
		
		long timeToRemove = System.currentTimeMillis() - 60000;
		
		if(this.slicedCache.containsKey(switchId)){
			HashMap<String, List<OFStatistics>> sliceStats = this.slicedCache.get(switchId);
			//loop through our current cache and set all packet/byte counts to 0
			Iterator<String> it = sliceStats.keySet().iterator();
			while(it.hasNext()){
				String slice = (String)it.next();
				List<OFStatistics> ofStats = this.slicedCache.get(switchId).get(slice);
				Iterator<OFStatistics> itStat = ofStats.iterator();
				while(itStat.hasNext()){
					OFStatistics stat = (OFStatistics)itStat.next();
					if(lastSeen.containsKey(stat)){
						if(lastSeen.get(stat) > timeToRemove && lastSeen.get(stat) > 0){
							itStat.remove();
							//lastSeen.remove(stat);
						}
					}else{
						itStat.remove();						
					}
				}
			}
		}
	}
	
	public List<FlowTimeout> getPossibleExpiredFlows(Long switchId){
		List<FlowTimeout> flowTimeouts = new ArrayList<FlowTimeout>();
		List<HashMap<Long, Slicer>> slices = parent.getSlices();

		for(HashMap<Long,Slicer> tmpSlices : slices){
			if(!tmpSlices.containsKey(switchId)){
				//switch not part of this slice
				continue;
			}
			flowTimeouts.addAll( this.parent.getProxy(switchId, tmpSlices.get(switchId).getSliceName()).getTimeouts());
		}
			
		return flowTimeouts;
	}
	
	public void checkExpireFlows(Long switchId){
		List<HashMap<Long, Slicer>> slices = parent.getSlices();

		for(HashMap<Long,Slicer> tmpSlices : slices){
			if(!tmpSlices.containsKey(switchId)){
				//switch not part of this slice
				continue;
			}
			this.parent.getProxy(switchId, tmpSlices.get(switchId).getSliceName()).checkExpiredFlows();
		}
	}
	
	
	/**
	 * retrieves the stats for the requested switch
	 * @param switchId
	 * @return
	 */
	public synchronized List <OFStatistics> getSwitchFlowStats(Long switchId){
		log.debug("Looking for switch stats: " + switchId);
		return cache.get(switchId);
	}
	
	public synchronized List <OFStatistics> getSlicedFlowStats(Long switchId, String sliceName){
		log.debug("Getting sliced stats for switch: " + switchId + " and slice " + sliceName);
		if(slicedCache.containsKey(switchId)){
			HashMap<String, List<OFStatistics>> tmpStats = slicedCache.get(switchId);
			if(tmpStats.containsKey(sliceName)){
				return tmpStats.get(sliceName);
			}
			return null;
		}
		return null;
	}
	
	public synchronized void setPortCache(Long switchId, HashMap<Short, OFStatistics> stats){
		portStatsCache.put(switchId, stats);
	}
	
	public synchronized OFStatistics getPortStats(Long switchId, short portId){
		HashMap<Short, OFStatistics> nodeStats = portStatsCache.get(switchId);
		return nodeStats.get(portId);
	}
	
	public synchronized HashMap<Short, OFStatistics> getPortStats(Long switchId){
		return portStatsCache.get(switchId);
	}
	
	
}
