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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * stores the configuration for a given port
 * this includes the vlan range, the port name, and id
 * @author aragusa
 *
 */
public class PortConfig {

	private VLANRange vlanRange;
	private short portId;
	private String portName;
	private static final Logger log = LoggerFactory.getLogger(PortConfig.class);
	
	public PortConfig(String portName, VLANRange vlans){
		if(vlans == null){
			throw new IllegalArgumentException("VLANRange not allowed to null!");
		}
		
		if(portName == null){
			throw new IllegalArgumentException("PortName not allowed to be null!");
		}
		
		this.vlanRange = vlans;
		this.portName = portName;
	}
	
	public PortConfig(){
		this.vlanRange = new VLANRange();		
	}
	
	/**
	 * returns the openflow PortID of this port
	 * @return portId of the port (integer)
	 */
	
	public short getPortId(){
		return portId;
	}
	
	/**
	 * returns the name of the port
	 * @return portName (string)
	 */
	
	public String getPortName(){
		return portName;
	}
	
	/**
	 * sets the vlan range of this port
	 * @param vlans (VLANRange object)
	 */
	
	public void setVLANRange(VLANRange vlans){
		this.vlanRange = vlans;
	}
	
	public VLANRange getVlanRange(){
		return this.vlanRange;
	}
	
	/**
	 * set the port name
	 * @param portName
	 */
	public void setPortName(String portName){
		this.portName = portName;
	}
	
	/**
	 * set the portId
	 * @param portId
	 */
	
	public void setPortId(short portId){
		this.portId = portId;
	}
	
	/**
	 * determines if a vlan is available on this port
	 * @param vlanId
	 * @return allowed/not allowed (boolean)
	 */
	
	public boolean vlanAllowed(short vlanId){
		log.debug("Checking the availability of vlan:" + vlanId);
		return vlanRange.getVlanAvail(vlanId);
	}
	
	
}
