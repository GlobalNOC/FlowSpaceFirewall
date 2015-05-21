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


import static org.junit.Assert.*;

import java.util.Date;



import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RateLimitTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testRateLimitInit(){
		RateTracker tracker = new RateTracker(100,1000);
		assertNotNull("tracker was properly created",tracker);
		assertTrue("initial rate = 0", tracker.getRate() == 0.0);
		assertTrue("RateLimit is set properly", tracker.getMaxRate() == 1000);
		tracker.setRate(200);
		assertTrue("Rate limit changed properly", tracker.getMaxRate() == 200);
	}
	
	@Test
	public void testRateLimit(){
		RateTracker tracker = new RateTracker(100,10);
		assertTrue(tracker.okToProcess());

		for(int i=0;i<500;i++){
			try {
				Thread.sleep(70);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tracker.okToProcess();
		}

		assertTrue("Rate Limit: " + tracker.getRate(), tracker.getRate() >= 9);
	}
	
	@Test
	public void testRateLimitBig(){
		RateTracker tracker = new RateTracker(1000,10);
		assertTrue(tracker.okToProcess());

		for(int i=0;i<5000;i++){
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tracker.okToProcess();
		}

		assertTrue("Rate Limit: " + tracker.getRate(), tracker.getRate() >= 9);
	}
	
	@Test
	public void testRateLimitFast(){
		RateTracker tracker = new RateTracker(2000,10000);
	
		CircularFifoQueue<Date> fifo = tracker.getFifo();
		Date now = new Date();
		for(int i =0; i< 1000;i++){
			fifo.add(now);
		}
		
		assertTrue("Tracker Rate is " + tracker.getRate(), tracker.getRate() == (1000 * 1000));
		
	}
	
	
	
}
