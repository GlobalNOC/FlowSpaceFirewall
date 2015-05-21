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
	
	
	public double getRate(){
		return get_rate_helper(null);
	}
	
	public double getRate(Date now){
		return get_rate_helper(now);
	}
	
	
	public CircularFifoQueue<Date> getFifo(){
		return this.myFifo;
	}
	
	private synchronized double get_rate_helper(Date now){
		if(myFifo.size() <= myFifo.maxSize() / 10){
			log.debug("circular queue is too small, returning 0");
			return 0;
		}
		log.debug("calculating current rate");
		
		long end;
		if(now == null){
			end = myFifo.get(myFifo.size()-1).getTime();
		}else{
			end = now.getTime();
		}
		
		long start = myFifo.get(0).getTime();
		//log.error("#packets = " + myFifo.size() + " / end = " + end + " start = " + start);

		if(end - start <= 0){
			//this means that our entire circular queue has the same ms
			//so that means our packet rate is fifo size * 1000
			return myFifo.size() * 1000;
		}
		return (myFifo.size() / ((end - start) / 1000.0));
	}
	
	public void setRate(int flowRate){
		this.myRate = flowRate;
	}
	
	public int getMaxRate(){
		return this.myRate;
	}
	
}