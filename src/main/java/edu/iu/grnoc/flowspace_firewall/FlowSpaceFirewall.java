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
import java.util.Collection;
import java.util.Collections;
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
        logger.info("Switch " + switchId + " has joined");
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        
        this.switches.add(sw);
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
	
	public void addFlowCache(long switchId, String sliceName, OFFlowMod flowMod){
		this.statsCacher.addFlowCache(switchId, sliceName,flowMod);
	}
	
	public List<IOFSwitch> getSwitches(){
		return this.switches;
	}

	public HashMap<Short, OFStatistics> getPortStats(long switchId){
		return statsCacher.getPortStats(switchId);
	}
	
	public OFStatistics getPortStats(long switchId, short portId){
		return statsCacher.getPortStats(switchId, portId);
	}
	
	public List<OFStatistics> getSlicedFlowStats(long switchId, String sliceName){
		return statsCacher.getSlicedFlowStats(switchId, sliceName);
	}
	
	public List<OFStatistics> getStats(long switchId){
		return statsCacher.getSwitchStats(switchId);
	}
	
	public List<Proxy> getSwitchProxies(long switchId){
		return controllerConnector.getSwitchProxies(switchId);
	}
	
	public synchronized void setSlice(long dpid, String name, Slicer slice){
		for(HashMap<Long, Slicer> hash : this.slices){
			if(hash.containsKey(dpid)){
				if(hash.get(dpid).getSliceName().equals(name)){
					hash.put(dpid, slice);
					return;
				}
			}
		}
		//if we made it here then there was no slicer...
		HashMap<Long, Slicer> tmp = new HashMap<Long,Slicer>();
		tmp.put(dpid, slice);
		this.slices.add(tmp);
	}
	
	public synchronized void removeSlice(long dpid, String name){
		for(HashMap<Long, Slicer> hash : this.slices){
			if(hash.containsKey(dpid)){
				if(hash.get(dpid).getSliceName().equals(name)){
					hash.remove(dpid);
				}
			}
		}
		
		for(int i=0;i< this.slices.size(); i++){
			HashMap<Long, Slicer> tmp = this.slices.get(i);
			if(tmp.isEmpty()){
				this.slices.remove(i);
			}
		}
		
	}
	
	@Override
	public void switchRemoved(long switchId) {
		logger.error("Switch removed!");
		List <Proxy> proxies = controllerConnector.getSwitchProxies(switchId);
		Iterator <Proxy> it = proxies.iterator();

		while(it.hasNext()){
			Proxy p = it.next();
			p.disconnect();
			it.remove();
		}
		
		this.statsCacher.clearCache(switchId);
		
		Iterator <IOFSwitch> switchIt = this.switches.iterator();
		while(switchIt.hasNext()){
			IOFSwitch tmpSwitch = switchIt.next();
			if(tmpSwitch.getId() == switchId){
				switchIt.remove();
			}
		}
				
	}
	
	public synchronized HashMap<Long, Slicer> getSlice(String name){
		List<HashMap<Long,Slicer>> mySlices = Collections.synchronizedList(this.slices);
		
		Iterator <HashMap<Long,Slicer>> it = mySlices.iterator();
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
	
	public synchronized List<HashMap<Long,Slicer>> getSlices(){
		List<HashMap<Long,Slicer>> slices = Collections.synchronizedList(this.slices);
		logger.debug("slices size: "+slices.size());
		return slices;
	}

	@Override
	public void switchActivated(long switchId) {
		logger.debug("Switch Activated");
	}
	
	public void removeProxy(Long switchId, Proxy p){
		this.controllerConnector.removeProxy(switchId, p);
	}

	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		//nothing to do here
	}

	@Override
	public void switchChanged(long switchId) {
		//we don't do anything here
		logger.debug("Switch changed!");
	}
	
	/**
	 * reloadConfig - reloads the configuration of FSF
	 * disconnects/re-connects/and connects to slices as the FSF config specifies
	 * this is only called via the web-service
	 */
	
	public boolean reloadConfig(){
		ArrayList<HashMap<Long, Slicer>> newSlices;
		ArrayList<Proxy> toBeRemoved = new ArrayList<Proxy>();
		//have our new configuration
		//need to put it in place
		try {
			newSlices = ConfigParser.parseConfig("/etc/fsfw/fsfw.xml");

			//remove the existing slices
			Iterator <HashMap<Long,Slicer>> sliceIt = this.slices.iterator();
			while(sliceIt.hasNext()){
				@SuppressWarnings("unused")
				HashMap<Long,Slicer> tmp = sliceIt.next();
				sliceIt.remove();
			}
			
			//push this into our this.slices variable
			for(HashMap<Long,Slicer> slice : newSlices){
				for(Long dpid : slice.keySet()){
					this.setSlice(dpid, slice.get(dpid).getSliceName(), slice.get(dpid));
				}
			}
			
			List <Proxy> proxies = controllerConnector.getAllProxies();
							
			logger.warn("number of proxies " + proxies.size() );
			for(Proxy p : proxies){
				//we now know the proxy and the switch (so we know the slice name and the switch)
				//now we need to find the slice in the newSlices variable and set the proxy to it
				boolean updated = false;
				for(HashMap<Long, Slicer> slice: newSlices){
					logger.debug("number of switches in newslice:"+slice.keySet().size());
					if(slice.containsKey(p.getSwitch().getId()) && slice.get(p.getSwitch().getId()).getSliceName().equals(p.getSlicer().getSliceName())){
				  		p.setSlicer(slice.get(p.getSwitch().getId()));
				        logger.warn("Slice " + p.getSlicer().getSliceName() + " was found, setting updated to true");
					    updated = true;			        		
					    slice.remove(p.getSwitch().getId());
					}
				}
				
				if(!updated){
					logger.warn("Slice "
							+p.getSlicer().getSliceName()+":" + p.getSwitch().getStringId() +" was not found, removing");
					p.disconnect();
					toBeRemoved.add(p);
				}
			}
		
			//so now we have updated all the ones connected and removed all the ones that are no longer there
			//we still need to connect up new ones
			Iterator <HashMap<Long,Slicer>> sliceIt2 = newSlices.iterator();
			logger.warn("Number of items left in newSlices: " + newSlices.size());
			while(sliceIt2.hasNext()){
				//iterate over the slices
				HashMap<Long,Slicer> slice = sliceIt2.next();
				//for each slice iterator over any switches configured
				if(slice.isEmpty()){
					
				}
				else{	
					for(Long dpid: slice.keySet()){
						//connect it up
						IOFSwitch sw = floodlightProvider.getSwitch(dpid);
						if(sw == null){
							logger.debug("Switch was not connected... can't add the proxy");
						}else{
							Slicer vlanSlicer = slice.get(dpid);
							controllerConnector.addProxy(dpid, new Proxy(sw, vlanSlicer, this));						
						}
					}
				}
			}
			
			//remove any proxies that are to be removed
			for(Proxy p: toBeRemoved){
				this.removeProxy(p.getSwitch().getId(), p);
				this.removeSlice(p.getSwitch().getId(), p.getSlicer().getSliceName());
			}
			
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
        logger.debug("Number of slices after reload: "+this.slices.size());
		return true;
	}


	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if(sw == null || !sw.isActive()){
			return Command.CONTINUE;
		}
		logger.debug("Received: " + msg.toString() + " from switch: " + sw.getStringId());
		List <Proxy> proxies = controllerConnector.getSwitchProxies(sw.getId());
		
		if(proxies == null){
			logger.warn("No proxies for switch: " + sw.getStringId());
			return Command.CONTINUE;
		}

		
		for(Proxy p : proxies){
			if(!p.getAdminStatus()){
				logger.debug("slice disabled... skipping");
			}else{
				try{
					logger.debug("attempting to send " + msg.toString() + " to slice: " + p.getSlicer().getSliceName() + " from switch: " + sw.getStringId());
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
        String configFile = "/etc/fsfw/fsfw.xml";
        Map<String,String> config = context.getConfigParams(this);
        if(config.containsKey("configFile")){
        	configFile = config.get("configFile");
        }
        
		try{
			this.slices = ConfigParser.parseConfig(configFile);
		}catch (SAXException e){
			logger.error("Problems parsing " + configFile + ": " + e.getMessage());
		}catch (IOException e){
			logger.error("Problems parsing " + configFile + ": " + e.getMessage());
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
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
		switches = new ArrayList<IOFSwitch>();
		//start up the stats collector timer
		statsTimer = new Timer("StatsTimer");
		statsCacher = new FlowStatCacher(this);
		this.statsCacher.loadCache();
		statsTimer.scheduleAtFixedRate(statsCacher, 0, 10 * 1000);
		
		//start up the controller connector timer
		controllerConnectTimer = new Timer("ControllerConnectionTimer");
		controllerConnector = new ControllerConnector();
		controllerConnectTimer.scheduleAtFixedRate(controllerConnector, 0, 10 * 1000);
		
		restApi.addRestletRoutable(new FlowSpaceFirewallWebRoutable());
		
	}
	
	public boolean setSliceAdminState(Long dpid, String sliceName, boolean state){
		
		List<Proxy> proxies = this.controllerConnector.getSwitchProxies(dpid);
		for(Proxy p: proxies){
			if(p.getSlicer().getSliceName().equals(sliceName)){
				logger.info("Setting Slice: " + sliceName + " admin state to " + state);
				p.setAdminStatus(state);
				return true;
			}
		}
		
		return false;
	}

	public Proxy getProxy(Long dpid, String sliceName){
		List<Proxy> proxies = this.controllerConnector.getSwitchProxies(dpid);
		for(Proxy p: proxies){
			if(p.getSlicer().getSliceName().equals(sliceName)){
				return p;
			}
		}
		return null;
	}
	
/*	@Override
	public List<OFStatistics> getSliceFlows(String sliceName, Long dpid) {
		return this.statsCacher.getSlicedFlowStats(dpid, sliceName));
	}
*/
/*	@Override
	public HashMap<String, Object> getSliceStatus(String sliceName, Long dpid) {
		// TODO Auto-generated method stub
		return null;
	}
	*/
}