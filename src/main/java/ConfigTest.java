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

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import edu.iu.grnoc.flowspace_firewall.CommandLineArgs;
import edu.iu.grnoc.flowspace_firewall.ConfigParser;

public class ConfigTest{
	
	public static void main(String []args){
		CommandLineArgs myArgs = new CommandLineArgs(args);
		CmdLineParser parser = new CmdLineParser(myArgs);
		
		try{
			parser.parseArgument(args);
		} catch(CmdLineException e){
			System.exit(1);
		}
		
		String config = myArgs.getConfig().toString();
		
		try{
			ConfigParser.parseConfig(config);
		} catch(Exception e){
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		System.out.println("Config looks good.");
		System.exit(0);
	}
}