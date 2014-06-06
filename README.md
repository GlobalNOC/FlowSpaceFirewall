FlowSpaceFirewall
=================

FlowSpace Firewall (FSFW) provides network virtualization of OpenFlow enabled switches.  The virtualization happens on a per-vlan tag per-interface basis.  Instead of attempting to interpret a rule and make possible modifications (like FlowVisor) to a flow mod, FlowSpace firewall either allows a rule to pass through or rejects it, and sends an error back to the controller.

FlowSpace Firewall also provides the ability to slice flow statistics based on the defined flow space and send each controller only the set of flows that it is able to modify.

The ultimate goal of FlowSpace Firewall is to provide the ability for multiple controllers to control a single openflow switch, without those controllers being able to step on each others flow rules. What seperates it from other OpenFlow virtualization tools (such as FlowVisor) is its limited scope, currently only slicing on vlan/interface, and the ability to configure it with "friendly names" like port name and node name instead of DPID and port id (as those may change).  The limited scope allows for simpler and faster slicing of requests.  

FlowSpace firewall is configured using an XML file, the configuration file accepts vlan ranges, allowing for less configuration than other SDN virtualization tools.  The ability to use friendly names provides the ability for port ids to change without having to update the configuration.  

FlowSpace Firewall is a module that plugs into Floodlight (an opensource openflow controller) which is developed in Java.

For assistance please contact fsfw-users@grnoc.iu.edu or join our list at https://mail1.grnoc.iu.edu/mailman/listinfo/fsfw-users

FlowSpace Firewall is Distributed under the Apache 2 License and is Copyright 2013 Indiana University
