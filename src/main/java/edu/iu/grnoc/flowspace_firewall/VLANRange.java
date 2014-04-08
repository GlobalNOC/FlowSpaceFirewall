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

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VLANRange {

	//min and max ranges
	public static final short MAX_VLAN = 4095;
	public static final short MIN_VLAN = 1;
	public static final short UNTAGGED = -1;
	
	private static final Logger log = LoggerFactory.getLogger(VLANRange.class);
	
	//these prevent us from having to loop through
	//and determine if the port allows a wildcard
	private boolean wildcard = false;
	
	//hastable that lets us see if a vlan is allowed
	private Hashtable <Short, Boolean> vlans = new Hashtable <Short, Boolean>();
	
	public VLANRange(){
		//create the vlanRange and set everything to not allowed
		for(short i=MIN_VLAN;i<=MAX_VLAN;i++){
			vlans.put(i, false);
		}
		vlans.put(UNTAGGED, false);
	}
		
	public VLANRange(short vlans[],boolean status){
		//create a vlan range with an array of vlans and setting the status
		//for each of them
		for(short i=MIN_VLAN; i<=MAX_VLAN; i++){
			this.vlans.put(i,!status);
		}
		this.vlans.put(UNTAGGED, false);
		
		for(int i=0; i< vlans.length; i++){
			this.setVlanAvail(vlans[i], status);
		}
		
		this.wildcard = this.allowVlanWildcard();
	}
	
	/**
	 * sets a vlans status for this vlanRange.
	 * @param vlanId the vlanId to set the status for
	 * @param status the status of the vlan (boolean) allowed/not allowed
	 */
	public void setVlanAvail(short vlanId, boolean status) throws IllegalArgumentException{
		if(!validVlan(vlanId)){
			throw new IllegalArgumentException("VLAN ID " + vlanId + " is out of range for valid vlan tags");
		}
		vlans.put(vlanId, status);
		this.wildcard = this.allowVlanWildcard();
	}
	
	/**
	 * returns if wildcards are allowed or not
	 * @return
	 */
	public boolean allowWildcard(){
		return wildcard;
	}
	
	/**
	 * determins if a given vlan id is available
	 * @param vlanId
	 * @return boolean
	 */
	

	private boolean validVlan(short vlanId){
		if(vlanId != UNTAGGED && (vlanId > MAX_VLAN || vlanId < MIN_VLAN)){
			return false;
		}
		return true;
	}
	
	public boolean getVlanAvail(short vlanId) throws IllegalArgumentException{
		if(!validVlan(vlanId)){
			throw new IllegalArgumentException("VLAN ID " + vlanId + " is out of range for valid vlan tags");
		}

		log.debug("Looking for available for vlan: " + vlanId);
		return vlans.get(new Short(vlanId));
	}
	
	/**
	 * returns if the wildcard is allowed or not
	 * we currently aren't using this as wildcard vlan = bad
	 * @return
	 */
	private boolean allowVlanWildcard(){
		for(short i=MIN_VLAN; i<=MAX_VLAN; i++){
			if(!vlans.get(i)){
				return false;
			}
		}
		return true;
	}
	
}
