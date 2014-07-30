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
import org.junit.Rule;


import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VLANRangeTest {
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	/**
	 * tests the default init with no params
	 */
	@Test
	public void testDefaultInit() {
		VLANRange range = new VLANRange();
		assertNotNull("Range was created properly",range);
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)1));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)2));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)3));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)4));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)100));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)1000));
		assertFalse("Default vlan avail is not allowed",range.getVlanAvail((short)4095));
	}

	/**
	 * test creating the vlan range based on
	 * an array of shorts and allowed or not allowed
	 */
	
	@Test
	public void testParamInit(){
		short[] allowed = new short[10];
		allowed[0] = 1;
		allowed[1] = 150;
		allowed[2] = 2;
		allowed[3] = 4;
		allowed[4] = 4095;
		allowed[5] = -1;
		allowed[6] = 5;
		allowed[7] = 100;
		allowed[8] = 1000;
		allowed[9] = 4000;
		
		VLANRange range = new VLANRange(allowed, true);
		
		assertNotNull("range was created properly", range);
		assertTrue("allowed vlan 1", range.getVlanAvail((short)1));
		assertTrue("allowed vlan 1000", range.getVlanAvail((short)1000));
		assertTrue("allowed vlan 2", range.getVlanAvail((short)2));
		assertFalse("not allwed vlan 3", range.getVlanAvail((short)3));
		assertFalse("not allowed vlan 101", range.getVlanAvail((short)101));
		assertTrue("allowed vlan 4000", range.getVlanAvail((short)4000));
		assertTrue("allowed vlan 4095", range.getVlanAvail((short)4095));
		assertTrue("allowed vlan 150", range.getVlanAvail((short)150));
		assertTrue("allowed vlan -1", range.getVlanAvail((short)-1));
		
		Short[] avail = range.getAvailableTags();
		
		assertTrue(avail.length == 10);
		assertTrue(avail[0] == 1);
		assertTrue(avail[1] == 150);
		assertTrue(avail[2] == 2);
		assertTrue(avail[3] == 4);
	}
	
	/**
	 * test creating the vlan range based on
	 * an array of shorts and allowed or not allowed
	 */
	@Test
	public void testParamInitFalse(){
		short[] allowed = new short[10];
		allowed[0] = 1;
		allowed[1] = 150;
		allowed[2] = 2;
		allowed[3] = 4;
		allowed[4] = 4095;
		allowed[5] = -1;
		allowed[6] = 5;
		allowed[7] = 100;
		allowed[8] = 1000;
		allowed[9] = 4000;
		
		VLANRange range = new VLANRange(allowed, false);
		
		assertNotNull("range was created properly", range);
		assertFalse("not allowed vlan 1", range.getVlanAvail((short)1));
		assertFalse("not allowed vlan 1000", range.getVlanAvail((short)1000));
		assertFalse("not allowed vlan 2", range.getVlanAvail((short)2));
		assertTrue("allwed vlan 3", range.getVlanAvail((short)3));
		assertTrue("allowed vlan 101", range.getVlanAvail((short)101));
		assertFalse("not allowed vlan 4000", range.getVlanAvail((short)4000));
		assertFalse("not allowed vlan 4095", range.getVlanAvail((short)4095));
		assertFalse("not allowed vlan 150", range.getVlanAvail((short)150));
		assertFalse("not allowed vlan -1", range.getVlanAvail((short)-1));
		
		Short[] avail = range.getAvailableTags();
		assertTrue(avail.length == 4086);
		assertTrue(avail[0] == 3);
		assertTrue(avail[1] == 6);
		assertTrue(avail[2] == 7);
		assertTrue(avail[3] == 8);
		
	}
	
	/**
	 * tests setting each vlan range independently
	 */
	@Test
	public void setVlanAvail(){
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)1, true);
		range.setVlanAvail((short)2, true);
		range.setVlanAvail((short)3, false);
		range.setVlanAvail((short)4, true);
		range.setVlanAvail((short)5, true);
		range.setVlanAvail((short)100, true);
		range.setVlanAvail((short)4000, true);
		range.setVlanAvail((short)-1, true);
		
		assertTrue("allowed vlan 1", range.getVlanAvail((short)1));
		assertFalse("not allowed vlan 1000", range.getVlanAvail((short)1000));
		assertTrue("allowed vlan 2", range.getVlanAvail((short)2));
		assertFalse("not allwed vlan 3", range.getVlanAvail((short)3));
		assertFalse("not allowed vlan 101", range.getVlanAvail((short)101));
		assertTrue("allowed vlan 100", range.getVlanAvail((short)100));
		assertTrue("allowed vlan 4000", range.getVlanAvail((short)4000));
		assertTrue("allowed vlan -1", range.getVlanAvail((short)-1));
		
		Short[] avail = range.getAvailableTags();
		assertTrue(avail.length == 7);
		assertTrue(avail[0] == 1);
		assertTrue(avail[1] == 2);
		assertTrue(avail[2] == 4);
		assertTrue(avail[3] == 5);
		assertTrue(avail[4] == 100);
		assertTrue(avail[5] == 4000);
		assertTrue(avail[6] == -1);
	}
	
	/**
	 * tests sending inputs outside of min/max vlan tags
	 */
	@Test
	public void testSetVlanAvailAllowedRange() throws IllegalArgumentException{
		VLANRange range = new VLANRange();
		assertNotNull("range was created properly", range);
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("VLAN ID 5000 is out of range for valid vlan tags");
		range.setVlanAvail((short)5000, true);
	}
	
	/**
	 * tests sending inputs outside of min/max vlan tags
	 */
	@Test
	public void testGetVlanAvailAllowedRange() throws IllegalArgumentException{
		VLANRange range = new VLANRange();
		assertNotNull("range was created properly", range);
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("VLAN ID 5000 is out of range for valid vlan tags");
		range.getVlanAvail((short)5000);
	}
	
	@Test
	public void testSetVlanAvailAllowedRangeNeg() throws IllegalArgumentException{
		VLANRange range = new VLANRange();
		assertNotNull("range was created properly", range);
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("VLAN ID -10 is out of range for valid vlan tags");
		range.setVlanAvail((short)-10, true);
	}
	
	@Test
	public void testCreateWithOutOfRangeVlan() throws IllegalArgumentException{
		short[] allowed = new short[10];
		allowed[0] = 1;
		allowed[1] = 150;
		allowed[2] = 2;
		allowed[3] = -1;
		allowed[4] = 4096;
		allowed[5] = 102;
		allowed[6] = 5;
		allowed[7] = 100;
		allowed[8] = 1000;
		allowed[9] = 4000;

		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("VLAN ID 4096 is out of range for valid vlan tags");
		VLANRange range = new VLANRange(allowed, true);
		assertNull(range);
	}
	
	@Test
	public void testWildcardValues(){
		VLANRange range = new VLANRange();
		assertNotNull("range was created properly",range);
		assertFalse("wildcard said no",range.allowWildcard());
		range.setVlanAvail((short)-1,true);
		for(short i=VLANRange.MIN_VLAN;i<=VLANRange.MAX_VLAN;i++){
			range.setVlanAvail(i,true);
		}
		assertTrue("Allowed the wildcard", range.allowWildcard());
		range.setVlanAvail((short)123,false);
		assertFalse("wildcard not allowed", range.allowWildcard());
	}
	
	@Test
	public void testCompareRanges(){
		
		short[] allowed = new short[9];
		allowed[0] = 1;
		allowed[1] = 150;
		allowed[2] = 2;
		allowed[3] = -1;
		allowed[4] = 102;
		allowed[5] = 5;
		allowed[6] = 100;
		allowed[7] = 1000;
		allowed[8] = 4000;
		VLANRange range = new VLANRange(allowed,true);
		short[] allowed2 = new short[4];
		allowed2[0] = 3;
		allowed2[1] = 151;
		allowed2[2] = 4;
		allowed2[3] = 409;
		VLANRange range2 = new VLANRange(allowed2,true);
		assertFalse(range.rangeOverlap(range2));
		
		short[] allowed3 = new short[4];
		allowed3[0] = 3;
		allowed3[1] = 151;
		allowed3[2] = 4;
		allowed3[3] = 2;
		VLANRange range3 = new VLANRange(allowed3,true);
		assertTrue(range.rangeOverlap(range3));
	}
	
}
