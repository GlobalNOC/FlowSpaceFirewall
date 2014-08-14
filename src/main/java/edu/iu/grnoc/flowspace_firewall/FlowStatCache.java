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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the stats for all of the switches
 * for quick retreiveal without overloading
 * the switches.  The Synchronized methods give
 * essentially a mutex for accessing the data
 * @author aragusa
 *
 */

public class FlowStatCache {

	//the logger
	private static final Logger log = LoggerFactory.getLogger(FlowStatCache.class);
	//the cache
	private HashMap<Long, List<OFStatistics>> cache;
	private HashMap<Long, HashMap<Short, OFStatistics>> portStatsCache;
	private HashMap<Long, HashMap<String, List<OFStatistics>>> slicedCache;
	private FlowSpaceFirewall parent;
	
	public FlowStatCache(FlowSpaceFirewall parent){
		cache = new HashMap<Long, List<OFStatistics>>();
		portStatsCache = new HashMap<Long, HashMap<Short, OFStatistics>>();
		slicedCache = new HashMap<Long, HashMap<String, List<OFStatistics>>>();
		this.parent = parent;
	}

	
	public List <IOFSwitch> getSwitches(){
		return this.parent.getSwitches();
	}
	
	public synchronized void clearFlowCache(Long switchId){
		
		cache.remove(switchId);
		slicedCache.remove(switchId);
		
	}
	
	/**
	 * sets the stats for the given switch
	 * @param switchId
	 * @param stats
	 */
	public synchronized void setFlowCache(Long switchId, List <OFStatistics> stats){
		cache.put(switchId, stats);
		
		//slice the stats and set the cache
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
			if(slicedCache.containsKey(switchId)){
				HashMap<String, List<OFStatistics>> slicedStats = slicedCache.get(switchId);
				slicedStats.put(slice.getSliceName(), FlowStatSlicer.SliceStats(slice,stats));
				this.parent.getProxy(switchId, slice.getSliceName()).setFlowCount(slicedStats.get(slice.getSliceName()).size());
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
