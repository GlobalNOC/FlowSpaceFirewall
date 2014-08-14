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

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class RateTracker {

	private CircularFifoQueue<Date> myFifo;
	private int myRate;
	private static final Logger log = LoggerFactory.getLogger(Proxy.class);
	
	public RateTracker(int size, int rate){
		myRate = rate;
		myFifo = new CircularFifoQueue<Date>(size);
	}
	
	public synchronized boolean okToProcess(){
		log.debug("checking on the rate for this slice");
		Date now = new Date();
		if(this.getRate(now) < myRate){
			log.debug("rate was ok, allowing, and updating");
			myFifo.add(now);
			return true;
		}else{
			log.debug("rate is over, not allowing");
			return false;
		}
	}
	
	
	public synchronized double getRate(){
		if(myFifo.size() == 0){
			log.debug("circular queue is empty, returning 0");
			return 0;
		}
		log.debug("calculating current rate");
		long end = myFifo.get(myFifo.size() - 1).getTime();
		long start = myFifo.get(0).getTime();
		log.debug("#packets = " + myFifo.size() + " / end = " + end + " start = " + start);
		if(end - start <= 0){
			return 0;
		}
		return (myFifo.size() / ((end - start)/1000.0));
	}
	
	public synchronized double getRate(Date now){
		if(myFifo.size() == 0){
			log.debug("circular queue is empty, returning 0");
			return 0;
		}
		log.debug("calculating current rate");
		long end = now.getTime() / 1000;
		long start = myFifo.get(0).getTime() / 1000;
		log.debug("#packets = " + myFifo.size() + " / end = " + end + " start = " + start);
		if(end - start <= 0){
			return 0;
		}
		return (myFifo.size() / (end - start));
	}
	
	public void setRate(int flowRate){
		this.myRate = flowRate;
	}
	
	public int getMaxRate(){
		return this.myRate;
	}
	
}