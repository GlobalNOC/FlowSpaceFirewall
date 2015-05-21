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

import java.io.File;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class CommandLineArgs {
	@Option(name = "-c", aliases = { "--config" }, required = true, usage = "Config to test.")
	private File config;
	
	private boolean errorFree = false;
	
	public CommandLineArgs(String... args){
		CmdLineParser parser = new CmdLineParser(this);
		parser.setUsageWidth(80);
		try{
			parser.parseArgument(args);
			
			if(!getConfig().isFile()){
				throw new CmdLineException(parser, "--config is not a valid file.");
			}
			
			errorFree = true;
		} catch(CmdLineException e){
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}
	}
	
	public boolean isErrorFree(){
		return errorFree;
	}
	
	public File getConfig() {
		return config;
	}
}