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

import org.junit.Test;

import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class XidMapTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testXidMapInit(){
		XidMap mapper = new XidMap();
		assertNotNull("mapper was properly created",mapper);
	}
	
	@Test
	public void testXid(){
		XidMap mapper = new XidMap();
		mapper.put(1, 100);
		mapper.put(2, 200);
		mapper.put(5000, 5);
		assertTrue("1 == 100 " , mapper.get(1) == 100);
		assertTrue("2 == 200 " , mapper.get(2) == 200);
		assertTrue("5000 = 5", mapper.get(5000) == 5);
		assertTrue("contains 1", mapper.containsKey(1));
		assertTrue("contains 2", mapper.containsKey(2));
		assertTrue("contains 5000", mapper.containsKey(5000));
		assertTrue("removed 1", mapper.remove(1) == 100);
		assertTrue("removed 2", mapper.remove(2) == 200);
		assertFalse("contains 1", mapper.containsKey(1));
		assertFalse("contains 2", mapper.containsKey(2));
		assertTrue("contains 5000", mapper.containsKey(5000));
		assertFalse("contains 5", mapper.containsKey(5));
		
	}
	
	@Test
	public void testXidRemoveToKey(){
		XidMap mapper = new XidMap();
		mapper.put(1, 100);
		mapper.put(2, 200);
		mapper.put(3, 300);
		mapper.put(4, 400);
		mapper.put(5, 500);
		mapper.put(6, 600);
		mapper.put(7, 700);
		mapper.put(8, 800);
		mapper.put(9, 900);
		assertTrue("contains 1", mapper.containsKey(1));
		assertTrue("contains 2", mapper.containsKey(2));
		assertTrue("contains 3", mapper.containsKey(3));
		assertTrue("contains 4", mapper.containsKey(4));
		assertTrue("contains 5", mapper.containsKey(5));
		assertTrue("contains 6", mapper.containsKey(6));
		assertTrue("contains 7", mapper.containsKey(7));
		assertTrue("contains 8", mapper.containsKey(8));
		assertTrue("contains 9", mapper.containsKey(9));
		assertTrue("removed to key 6", mapper.removeToKey(6));
		assertFalse("key 1 removed", mapper.containsKey(1));
		assertFalse("key 2 removed", mapper.containsKey(2));
		assertFalse("key 3 removed", mapper.containsKey(3));
		assertFalse("key 4 removed", mapper.containsKey(4));
		assertFalse("key 5 removed", mapper.containsKey(5));
		assertFalse("key 6 removed", mapper.containsKey(6));
		assertTrue("contains 7", mapper.containsKey(7));
		assertTrue("contains 8", mapper.containsKey(8));
		assertTrue("contains 9", mapper.containsKey(9));
		mapper.put(1, 100);
		mapper.put(2, 200);
		mapper.put(3, 300);
		mapper.put(4, 400);
		mapper.put(5, 500);
		mapper.put(6, 600);
		assertTrue("contains 1", mapper.containsKey(1));
		assertTrue("contains 2", mapper.containsKey(2));
		assertTrue("contains 3", mapper.containsKey(3));
		assertTrue("contains 4", mapper.containsKey(4));
		assertTrue("contains 5", mapper.containsKey(5));
		assertTrue("contains 6", mapper.containsKey(6));
		assertTrue("contains 7", mapper.containsKey(7));
		assertTrue("contains 8", mapper.containsKey(8));
		assertTrue("contains 9", mapper.containsKey(9));
		assertTrue("removed to key 9", mapper.removeToKey(9));
		assertTrue("contains 1", mapper.containsKey(1));
		assertTrue("contains 2", mapper.containsKey(2));
		assertTrue("contains 3", mapper.containsKey(3));
		assertTrue("contains 4", mapper.containsKey(4));
		assertTrue("contains 5", mapper.containsKey(5));
		assertTrue("contains 6", mapper.containsKey(6));
		assertFalse("key 7 removed", mapper.containsKey(7));
		assertFalse("key 8 removed", mapper.containsKey(8));
		assertFalse("key 9 removed", mapper.containsKey(9));
		assertTrue("1 == 100 " , mapper.get(1) == 100);
		assertTrue("2 == 200 " , mapper.get(2) == 200);
		assertTrue("3 == 300 " , mapper.get(3) == 300);
		assertTrue("4 == 400 " , mapper.get(4) == 400);
		assertTrue("5 == 500 " , mapper.get(5) == 500);
		assertTrue("6 == 600 " , mapper.get(6) == 600);
	}
	
	@Test
	public void testXidMax(){
		XidMap mapper = new XidMap();
		
		for(int i=1;i<2000;i++){
			mapper.put(i, i+2000);
		}
		
		assertFalse("does not have 1", mapper.containsKey(1));
		assertFalse("does not have 1000", mapper.containsKey(1000));
		assertTrue("does contain 1022", mapper.containsKey(1022));
		assertTrue("does contain 1999", mapper.containsKey(1999));
	}
	
}
