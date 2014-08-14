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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XidMap {

	private Map<Integer, Integer> xidMap;
	private static final int max_xid_size = 1000;
	
	private static final Logger log = LoggerFactory.getLogger(XidMap.class);
	
	public XidMap(){
		xidMap = Collections.synchronizedMap(
				new LinkedHashMap<Integer,Integer>(){
					/**
					 * 
					 */
					private static final long serialVersionUID = 1L;

					@Override
					public boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest)  
					{
						//when to remove the eldest entry
						return size() >= max_xid_size ;   //size exceeded the max allowed
					}
				}
				);
	}
	
	public synchronized boolean containsKey(int key){	
		return xidMap.containsKey(key);
	}
	public synchronized int remove(int key){
		return xidMap.remove(key);
	}
	public synchronized int get(Object key){
		return xidMap.get(key);
	}
	
	public synchronized void put(int key, int value) {
		log.debug("Mapping XID: %d to %d", key, value);
		xidMap.put(key, value);
	}
		
		
	public synchronized boolean removeToKey(int key) {
		if (!xidMap.containsKey(key) ){
			return false;
		}
		Set<Entry<Integer, Integer>> entrySet = xidMap.entrySet();
		Iterator<Entry<Integer, Integer>> it = entrySet.iterator();	
		while (it.hasNext()) {
			Entry<Integer, Integer> localEntry= it.next();
			it.remove();
			//once we've gotten to our xid, break out of this loop
			if (localEntry.getKey() == key){
				break;	
			}
		}
		return true;
	}
	
}

