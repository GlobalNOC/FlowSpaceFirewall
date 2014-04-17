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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import edu.iu.grnoc.flowspace_firewall.web.FlowSpaceFirewallWebRoutable;
import edu.iu.grnoc.flowspace_firewall.web.IFlowSpaceFirewallService;
import edu.iu.grnoc.flowspace_firewall.FlowStatCacher;

public class FlowSpaceFirewall implements IFloodlightModule, IOFMessageListener, IOFSwitchListener, IFlowSpaceFirewallService{

	protected static Logger logger;
    //private HashMap <Long, Proxy> proxies;
    protected IFloodlightProviderService floodlightProvider;

    private Timer statsTimer;
    private Timer controllerConnectTimer;
    
    private ArrayList<HashMap<Long, Slicer>> slices;
    private ArrayList<IOFSwitch> switches;
    private FlowStatCacher statsCacher;
    private ControllerConnector controllerConnector;
    protected IRestApiService restApi;
    
    
	@Override
	public String getName() {
		//return "edu.iu.grnoc.flowspace_firewall";
		return FlowSpaceFirewall.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	
	
	@Override
	public void switchAdded(long switchId) {
        logger.debug("Switch " + switchId + " has joined");
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        this.switches.add(sw);
        this.statsCacher.addSwitch(sw);
        //loop through all slices
        for(HashMap<Long, Slicer> slice: slices){
        	//loop through all switches in the slice
        	if(slice.containsKey(switchId)){
        		Slicer vlanSlicer = slice.get(switchId);
        		//build the controller channel
        		controllerConnector.addProxy(switchId, new Proxy(sw, vlanSlicer, this));
        	}
        }
	}

	public HashMap<Short, OFStatistics> getPortStats(long switchId){
		return statsCacher.getPortStats(switchId);
	}
	
	public OFStatistics getPortStats(long switchId, short portId){
		return statsCacher.getPortStats(switchId, portId);
	}
	
	public List<OFStatistics> getStats(long switchId){
		return statsCacher.getSwitchStats(switchId);
	}
	
	public List<Proxy> getSwitchProxies(long switchId){
		return controllerConnector.getSwitchProxies(switchId);
	}
	
	@Override
	public void switchRemoved(long switchId) {
		// TODO Auto-generated method stub
		List <Proxy> proxies = controllerConnector.getSwitchProxies(switchId);
		Iterator <Proxy> it = proxies.iterator();

		while(it.hasNext()){
			Proxy p = it.next();
			p.disconnect();
			it.remove();
		}
		
		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        this.switches.remove(sw);
        this.statsCacher.removeSwitch(sw);
				
	}
	
	public HashMap<Long, Slicer> getSlice(String name){
		Iterator <HashMap<Long,Slicer>> it = this.slices.iterator();
		while(it.hasNext()){
			HashMap<Long,Slicer> slice = it.next();
			Iterator <Long> dpidIt = slice.keySet().iterator();
			if(dpidIt.hasNext()){
				Long dpid = dpidIt.next();
				if(slice.get(dpid).getSliceName().equals(name)){
					return slice;
				}
			}
		}
		return null;
	}
	
	public List<HashMap<Long,Slicer>> getSlices(){
		return this.slices;
	}

	@Override
	public void switchActivated(long switchId) {
		//nothing to do here
		
	}

	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		//nothing to do here
	}

	@Override
	public void switchChanged(long switchId) {
		//we don't do anything here
	}
	
	/**
	 * reloadConfig - reloads the configuration of FSF
	 * disconnects/re-connects/and connects to slices as the FSF config specifies
	 * this is only called via the web-service
	 */
	
	@SuppressWarnings("unchecked")
	public boolean reloadConfig(){
		ArrayList<HashMap<Long, Slicer>> newSlices;
		//have our new configuration
		//need to put it in place

		try {
			this.slices = ConfigParser.parseConfig("/etc/fsfw/fsfw.xml");
			if(this.slices.size() == 0){
				logger.error("Unable to reload config due to a problem in the configuration!");
				return false;
			}
			//newSlices is a clone so we can modify it without modifying slices
			//we will use this to figure out which ones we have updated and which
			//slices need to be created and connected to a currently active switch
			newSlices = (ArrayList<HashMap<Long, Slicer>>) this.slices.clone();
			Iterator <IOFSwitch> it = this.switches.iterator();
			while(it.hasNext()){
				IOFSwitch sw = it.next();
				List <Proxy> proxies = controllerConnector.getSwitchProxies(sw.getId());
				if(proxies != null){
					Iterator <Proxy> proxyIt = proxies.iterator();
					while(proxyIt.hasNext()){
						Proxy p = proxyIt.next();
						//we now know the proxy and the switch (so we know the slice name and the switch)
						//now we need to find the slice in the newSlices variable and set the proxy to it
						boolean updated = false;
						for(HashMap<Long, Slicer> slice: newSlices){
				        	//loop through all switches in the slice
				        	if(slice.containsKey(sw.getId()) && slice.get(sw.getId()).getSliceName().equals(p.getSlicer().getSliceName())){
				        		p.setSlicer(slice.get(sw.getId()));
				        		slice.remove(sw.getId());
				        		if(slice.isEmpty()){
				        			newSlices.remove((Object) slice);
				        		}
				        		updated = true;
				        	}
				        }
						if(updated == false){
							p.disconnect();
							controllerConnector.removeProxy(sw.getId(), p);
						}
					}
				}
			}
			//so now we have updated all the ones connected and removed all the ones that are no longer there
			//we still need to connect up new ones
			Iterator <HashMap<Long,Slicer>> sliceIt = newSlices.iterator();
			while(sliceIt.hasNext()){
				//iterate over the slices
				HashMap<Long,Slicer> slice = sliceIt.next();
				//for each slice iterator over any switches configured
				for(Long dpid: slice.keySet()){
					if(this.switches.contains(dpid)){
						//connect it up
						IOFSwitch sw = floodlightProvider.getSwitch(dpid);
						Slicer vlanSlicer = slice.get(dpid);
		        		controllerConnector.addProxy(dpid, new Proxy(sw, vlanSlicer, this));
					}
				}
			}
			
			
			//change the standing slices to this
			this.slices = newSlices;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		} catch(ParserConfigurationException e){
			logger.error(e.getMessage());
			return false;
		} catch(XPathExpressionException e){
			logger.error(e.getMessage());
			return false;
		}
		return true;
	}


	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if(sw == null || !sw.isActive()){
			return Command.CONTINUE;
		}
		
		List <Proxy> proxies = controllerConnector.getSwitchProxies(sw.getId());
		if(proxies == null){
			return Command.CONTINUE;
		}
		Iterator <Proxy> it = proxies.iterator();
		while(it.hasNext()){
			Proxy p = it.next();
			if(!p.getAdminStatus()){
				logger.debug("slice disabled... skipping");
			}else{
				try{
					p.toController(msg,cntx);
				}catch (Exception e){
					//don't die please... just keep going and error the stack trace
					logger.error("FSFW experienced an error:" + e.getMessage(), e);
				}
			}
		}
		return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
            l.add(IFloodlightProviderService.class);
            l.add(IFlowSpaceFirewallService.class);
            return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IFlowSpaceFirewallService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(FlowSpaceFirewall.class);
        restApi = context.getServiceImpl(IRestApiService.class);
		//parses the config
		try{
			this.slices = ConfigParser.parseConfig("/etc/fsfw/fsfw.xml");
		}catch (SAXException e){
			logger.error("Problems parsing /etc/fsfw/fsfw.xml: " + e.getMessage());
		}catch (IOException e){
			logger.error("Problems parsing /etc/fsfw/fsfw.xml: " + e.getMessage());
		} catch(ParserConfigurationException e){
			logger.error(e.getMessage());
		} catch(XPathExpressionException e){
			logger.error(e.getMessage());
		}

		if(this.slices.size() == 0){
			logger.error("Problem with the configuration file!");
			throw new FloodlightModuleException("Problem with the Config!");
		}

        
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		
		floodlightProvider.addOFSwitchListener(this);
		floodlightProvider.addOFMessageListener(OFType.BARRIER_REPLY, this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFMessageListener(OFType.PORT_MOD, this);
		floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
		floodlightProvider.addOFMessageListener(OFType.ERROR,this);
		switches = new ArrayList<IOFSwitch>();
		//start up the stats collector timer
		statsTimer = new Timer("StatsTimer");
		statsCacher = new FlowStatCacher();
		statsTimer.scheduleAtFixedRate(statsCacher, 0, 10 * 1000);
		
		//start up the controller connector timer
		controllerConnectTimer = new Timer("ControllerConnectionTimer");
		controllerConnector = new ControllerConnector();
		controllerConnectTimer.scheduleAtFixedRate(controllerConnector, 0, 10 * 1000);
		
		restApi.addRestletRoutable(new FlowSpaceFirewallWebRoutable());
		
		
	}

	@Override
	public ArrayList<OFFlowMod> getSliceFlows(String sliceName, Long dpid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HashMap<String, Object> getSliceStatus(String sliceName, Long dpid) {
		// TODO Auto-generated method stub
		return null;
	}
}