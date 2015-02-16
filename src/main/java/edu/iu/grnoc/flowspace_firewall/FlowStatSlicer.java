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
import java.util.Iterator;
import java.util.List;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slices FlowStats based on the Slicer and the stats passed.
 * @author aragusa
 *
 */
public final class FlowStatSlicer {
	
	//the logger
	private static final Logger log = LoggerFactory.getLogger(FlowStatSlicer.class);

	
	//the only method we need here
	public static List <OFStatistics> SliceStats(Slicer slicer, List <OFStatistics> stats){
		
		//this holds our reply
		List <OFStatistics> reply = new ArrayList<OFStatistics>();
		
		if(stats == null){
			throw new IllegalArgumentException("FlowStat slicer got null stats!!");
		}
		
		log.debug("Slicing Stats:" + stats.toString());
		Iterator<OFStatistics> it = stats.iterator();

		//iterate over all the flows and build a flowmod for each.  Then send the flowmod
		//to the slicer, if it is allowed then this stat gets sent
		while(it.hasNext()){
			OFFlowStatisticsReply flowStat = (OFFlowStatisticsReply) it.next();
			OFFlowMod flowMod = new OFFlowMod();
			flowMod.setMatch(flowStat.getMatch());
			flowMod.setActions(flowStat.getActions());
			log.debug("Slicing flowStat:" + flowStat.toString());
			log.debug("Built FlowMod: " + flowMod.toString());
			List <OFFlowMod> flowMods = slicer.allowedFlows(flowMod);
			if(flowMods.size() != 0){
				if(slicer.getTagManagement()){
					List<OFAction> actions = flowStat.getActions();
					List<OFAction> newActions = new ArrayList<OFAction>();
					short length = 0;
					//loop through all the actions and remove any set_vlan_vid or strip_vlan actions
					for(OFAction act : actions){
						switch(act.getType()){
							case SET_VLAN_ID:
								break;
							case STRIP_VLAN:
								break;
							default:
								newActions.add(act);
								length += act.getLength();
						}
					}
					flowStat.setLength((short)(length + OFFlowStatisticsReply.MINIMUM_LENGTH));
					flowStat.setActions(newActions);
					flowStat.getMatch().setWildcards(flowStat.getMatch().getWildcardObj().wildcard(Wildcards.Flag.DL_VLAN));
					flowStat.getMatch().setDataLayerVirtualLan((short)0);
				}
				reply.add(flowStat);
			}
		}
		
		//do we need to collapse some of these down?
		//TODO: consolidate anything that was probably a wildcard port
		//how do we collapse them down?
		return reply;
	}
	
}
