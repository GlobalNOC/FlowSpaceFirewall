#!/usr/bin/perl

#--------------------------------------------------------------------
#----- FSFW CLI library
#-----
#----- Copyright(C) 2014 The Trustees of Indiana University
#--------------------------------------------------------------------
#----- $HeadURL:
#----- $Id:
#-----
#----- Library that acts as a CLI for the FSFW Webservices
#---------------------------------------------------------------------

package FSFW::CLI;

use strict;
use Term::ReadLine;    #using Term::ReadLine::Gnu however best practices say not to require it directly?
use GRNOC::Config;
use FindBin;
use Data::Dumper;
use GRNOC::WebService::Client;

#use Text::Table;
our $VERSION = "1.0.0";

sub new {

    my $proto = shift;
    my $class = ref($proto) || $proto;

    my %attributes = (
        host     => undef,
        user     => undef,
        password => undef,
        timeout  => 30,
        debug    => 0,
        @_,
    );

    my $self = \%attributes;

    bless( $self, $class );

    $self->_init();

    return $self;

}

sub _init {
    my $self = shift;

    $self->{config} = GRNOC::Config->new( config_file => '/etc/fsfw/cli_config.xml' );
    my $base_url = $self->{'config'}->get('/config/base_url');
    my $port     = $self->{'config'}->get('/config/port');
    $base_url              = 'http://localhost';
    $port                  = '8080';
    $self->{history}       = [];
    $self->{history_index} = 0;

    $self->{'ws'} = GRNOC::WebService::Client->new(
        url     => "$base_url:$port/admin/switches/json",
        uid     => 'test_user',
        passwd  => 'supersecretpassword',
        realm   => 'foo',
        usePost => 0,
    );

    $self->build_command_list();

    $self->{'term'} = Term::ReadLine->new('FSFW CLI');
    $self->set_prompt('FSFW#');

    my $attribs = $self->{'term'}->Attribs;

    #setting completion function up for Term::Readline
    my $cli = $self;
    $attribs->{completion_function} = sub {
        my ( $text, $line, $start ) = @_;
        $self->command_complete( $text, $line, $start );
    };

    return;
}

sub expand_commands {
    my $self  = shift;
    my $input = shift;

    my $new_text;

    my @input_parts = split( " ", $input );
    foreach my $input_part (@input_parts) {
        $input_part = quotemeta($input_part);
    }
    my $command_list  = $self->get_command_list();
    my $times_matched = 0;

    my $exact_matches   = {};
    my $partial_matches = {};

    #for each command, use exact match for each step avaliable, bomb out if there are multiple partial matches

    foreach my $command (@$command_list) {
        my $matching_parts = 0;

        my @command_parts = split( " ", $command );
        for ( my $i = 0 ; $i < scalar(@input_parts) ; $i++ ) {
            unless ( $partial_matches->{$i} ) {
                $partial_matches->{$i} = {};
            }

            if ( $command_parts[$i] =~ /^$input_parts[$i]$/ ) {
                $exact_matches->{$i} = $command_parts[$i];
            }
            elsif ( $command_parts[$i] =~ /^$input_parts[$i].*/ ) {
                $matching_parts++;
                $partial_matches->{$i}->{ $command_parts[$i] } = 1;

                #print "$command_parts[$i] matches $input_parts[$i]\n";
            }

        }

    }
    for ( my $i = 0 ; $i < scalar(@input_parts) ; $i++ ) {
        if ( $exact_matches->{$i} ) {
            $new_text .= "$exact_matches->{$i} ";
        }
        elsif ( $partial_matches->{$i} && scalar( keys %{ $partial_matches->{$i} } ) == 1 ) {
            my @values = keys %{ $partial_matches->{$i} };

            $new_text .= "$values[0] ";
        }
        else {

            #no partial or exact matches, or multiple partial matches
            warn "command not found\n";
            return ( 1, $input );
        }
    }
    chop($new_text);
    return ( 0, $new_text );

    # if ( $new_text && $times_matched == 1 ) {
    #     return ( 0, $new_text );
    # }
    # elsif ( $times_matched < 1 ) {
    #    # print "Command not found!\n";
    # }
    # elsif ( $times_matched > 1 ) {
    # 	my $new_command;
    # 	my $low_bar=0;
    # 	foreach my $command (keys %$exact_match){
    # 	    if ($exact_match->{$command} > $low_bar){
    # 		warn "found exact match $command";
    # 		$new_command=$command;
    # 	    }
    # 	}
    # 	if($new_command){
    # 	    #print "expanding $input to $new_command\n";
    # 	    return (0, $new_command);
    # 	}
    #     print "Command not unique!\n";
    # }

    # return ( 1, $input );
}

sub command_complete {
    my $self = shift;

    my ( $text, $line, $start ) = @_;
    my $command_list = $self->get_command_list();
    my @matches_at_level;

    # $line= quotemeta($line);

    my @text_parts = split( " ", $line );
    foreach (@text_parts) {
        $_ = quotemeta($_);
    }
    if ( $line eq "" ) {

        #warn "Line is empty\n\n";
        my @return;
        foreach my $command (@$command_list) {
            my @command_parts = split( " ", $command );
            my $last_word = $command_parts[0];
            push( @return, $command_parts[0] );

        }
        return @return;
    }

    foreach my $command (@$command_list) {
        my $offset = 0;

        my $is_match      = 1;
        my @command_parts = split( " ", $command );
        my $last_word     = $command_parts[0];

        #default to assuming the whole line matches until we find a word that doesn't in our current depth
        #for n words in line
        # if all words match and there are no options after it.. this is a full command, woot you've hit the end of the line
        # if all words match and you have more than one match, your last word options should be all options that match at this depth
        # if all words match and there are no other matches at this depth, give next depth option

        #warn "number of parts in arry $#text_parts";
        for ( my $i = 0 ; $i <= $#text_parts ; $i++ ) {

            #	    warn "command: $command_parts[$i], text:$text_parts[$i]\n";
            unless ( $matches_at_level[$i] ) {
                $matches_at_level[$i] = {};
            }
            unless ( $matches_at_level[ $i + 1 ] ) {
                $matches_at_level[ $i + 1 ] = {};
            }

            my $is_exact_match = 0;
            if ( $command_parts[$i] =~ /^$text_parts[$i].*/ ) {

                #print "matched $command_parts[$i]\n";
                #$last_word = $command_parts[$i];

                if ( $i == $#text_parts ) {

                    #print "adding at level $i\n";
                    #if we've started the next command only accept full matches for additions to the list of matches?
                    unless ( $line =~ /.*?\s+$/ ) {

                        #print "index $i, $command_parts[$i] matches $text_parts[$i]";
                        $matches_at_level[$i]->{ $command_parts[$i] } = 1;
                    }

                }
            }
            elsif ( $is_match && $i == $#text_parts && $text eq "" ) {

                #everything matches up to this point, add next option
                #$matches_at_level[$i]->{$command_parts[$i]} =1;
                #$matches_at_level[$i+1]->{$command_parts[$i+1]}=1; #=  $command_parts[$i+1];
            }
            else {
                if ( $i == $#text_parts ) {

                    # print "index $i, $command_parts[$i] does not match $text_parts[$i]";
                }
                $is_match = 0;
                last;
            }

            if ( $command_parts[$i] =~ /^$text_parts[$i]$/ ) {

                #exactly matches, so add current line and next line to matches at levels
                if ( $i == $#text_parts ) {

                    # print "index $i, $command_parts[$i] exactly matches $text_parts[$i]";
                    $matches_at_level[$i]->{ $command_parts[$i] } = 1;
                    $matches_at_level[ $i + 1 ]->{ $command_parts[ $i + 1 ] } = 1;
                }

            }

        }

        #if ($command =~ /^$line$/){
        #command is complete, perfect match don't add any options?
        #    return;
        #}

    }

    #warn Dumper (\@matches_at_level);
    #warn scalar(keys %{$matches_at_level[$#text_parts]});
    if ( $matches_at_level[$#text_parts] && ( scalar( keys %{ $matches_at_level[$#text_parts] } ) > 1 ) ) {

        #multiple matches at top level
        my @return = keys %{ $matches_at_level[$#text_parts] };

        #	print "multiple matches at this level $#text_parts : returning ".Dumper(\@return);
        return @return;
    }
    if ( $matches_at_level[$#text_parts] && ( scalar( keys %{ $matches_at_level[$#text_parts] } ) == 1 ) ) {
        if ( $matches_at_level[ $#text_parts + 1 ] && scalar( keys %{ $matches_at_level[ $#text_parts + 1 ] } ) ) {
            my @return = keys %{ $matches_at_level[ $#text_parts + 1 ] };

            # print "Only one match at current level returning next level: ".Dumper(\@return);
            #return @return;
            return @return;
        }
        my @return = keys %{ $matches_at_level[$#text_parts] };

        #print "Only one match at current level and no next level matches yet: ".Dumper(\@return);
        return @return;
    }

    #print "found no matches at $#text_parts \n";
    return;

    #my @return = keys (%matches);
    #print Dumper (\%matches);
    #return @return;
}

#stubbed out in case we ever have a legitimate auth system for the WS.
sub login {

    my $self = shift;

    return 1;

}

sub build_command_list {

    my $self = shift;
    my $ws   = $self->{'ws'};

    my $base_url = $self->{'config'}->get('/config/base_url');
    my $port     = $self->{'config'}->get('/config/port');
    $base_url = 'http://localhost';
    $port     = '8080';

    $self->{'possible_commands'} = [ 'show slices', 'show switches','set slice status'. 'help', '?', 'quit', 'exit' ];

    my @expandable_commands = ( 'show status', 'show flows', 'set slice status' );

    $ws->set_url("$base_url:$port/fsfw/admin/slices/json");
    my $slices_obj = $ws->foo();

    #print Dumper ($slices_obj);
    my @slices;
    foreach my $slice ( keys %$slices_obj ) {
        push( @slices, $slice );
    }
    unless ( grep { defined $_ } @slices ) {
        die "No slices found, check if FSFW is running? exiting.";
    }

    $ws->set_url("$base_url:$port/fsfw/admin/switches/json");
    my $dpids_obj = $ws->foo();

    unless ( grep { defined $_ } @$dpids_obj ) {
        print "No switches found connected to FSFW, show status and show flows will be unavailable.\n\n";
    }

    my $dpid_per_slice = {};
    foreach my $slice (@slices) {
        $dpid_per_slice->{$slice} = [];
    }

    foreach my $switch (@$dpids_obj) {
        foreach my $slice (@slices) {
            push @{ $dpid_per_slice->{$slice} }, $switch->{'dpid'};
        }
    }

    foreach my $expandable_command (@expandable_commands) {
        foreach my $slice (@slices) {
            foreach my $dpid ( @{ $dpid_per_slice->{$slice} } ) {

		if($expandable_command eq 'set slice status'){
		    push (@{ $self->{'possible_commands'} }, "$expandable_command $slice $dpid enable");
		    push (@{ $self->{'possible_commands'} }, "$expandable_command $slice $dpid disable");
		}else{
		
		    push( @{ $self->{'possible_commands'} }, "$expandable_command $slice $dpid" );
		}
            }
        }
    }

    return;
}

sub get_command_list {
    my $self = shift;
    return $self->{'possible_commands'};
}

sub set_prompt {
    my $self = shift;
    $self->{'prompt'} = shift;
    return;
}

sub get_prompt {
    my $self = shift;
    return $self->{'prompt'};
}

sub handle_input {
    my $self        = shift;
    my $input       = shift;
    my $insert_text = 0;
    my $ws          = $self->{'ws'};
    my $base_url    = $self->{'config'}->get('/config/base_url');
    my $port        = $self->{'config'}->get('/config/port');
    $base_url = 'http://localhost';
    $port     = '8080';
    ( $insert_text, $input ) = $self->expand_commands($input);

    if ( $input =~ /^exit$/ || $input =~ /^quit$/ ) {
        exit;
    }
    if ( $input =~ /^help$/ || $input =~ /^\?$/ ) {
        print <<END;
show slices

     Returns a list of slices, and for each slice a list of DPIDs configured to have access to it

show switches

     Returns details of each switch connected to FSFW:

show status [slice] [dpid]

     returns status of the slice with parameters:

set slice status [slice] [dpid] [status]

     sets the admin status of the slice

show flows [slice] [dpid] 

     returns all flows for this dpid with metadata, match and actions for each. 
     
quit | exit
     exit application

help
    returns this message

END

    }
    elsif ( $input =~ /^show switches$/ ) {

        my $dpid = $1 || undef;
        $ws->set_url("$base_url:$port/fsfw/admin/switches/json");
        my $status_obj = $ws->foo();

        unless ( grep { defined $_ } @$status_obj ) {
            print "No switches found attached FSFW\n";
        }
        foreach my $switch (@$status_obj) {
            if ( $dpid && $switch->{'dpid'} != $dpid ) {
                next;
            }
            my ($address) = $switch->{'inetAddress'} =~ /\/(\S+):\d+/;
	    print "DPID:\t$switch->{'dpid'}\n";
            print "IP:\t$address\n";
            print "Vendor:\t" . $switch->{'descriptionStatistics'}->{'manufacturerDescription'} . "\n";
            print "Device:\t" . $switch->{'descriptionStatistics'}->{'hardwareDescription'} . "\n";
            print "Software Version:\t" . $switch->{'descriptionStatistics'}->{'softwareDescription'} . "\n";
            print "Ports:\n";
            print "Name\tPort Number\tStatus\n\n";

            #my $port_table=Text::Table->new("Port Name","Port Number", "Status");
            foreach my $port ( @{ $switch->{'ports'} } ) {

                my $port_num = $self->_unsign_int( $port->{'portNumber'} );

                print "$port->{'name'}\t$port_num\tUP\n";

            }
            print "\n\n";
        }

    }
    elsif ( $input =~ /^show slices$/ ) {
        $ws->set_url("$base_url:$port/fsfw/admin/slices/json");
        my $slices = $ws->foo();

        foreach my $slice ( keys %$slices ) {

            my $dpids = $slices->{$slice};
            print "Slice Name: $slice\n\nSwitches Configured for Slice:\n";

            unless ( grep { defined $_ } @$dpids ) {
                print "No switches.\n";
            }

            foreach my $dpid (@$dpids) {
                print "$dpid\n";
            }
            print "\n";
        }

        #print Dumper ($slices);
    }
    elsif ( $input =~ /^show flows (\S+) (\S+)/ ) {

        #warn "showing flows.";
        my $vlan_id;
        my $port_id;

        $ws->set_url("$base_url:$port/fsfw/flows/$1/$2/json");
        my $flows = $ws->foo();

        #print Dumper ($flows); #NONPROD

        unless ( defined($flows) && grep { defined $_ } @$flows ) {
            print "No Flows to display\n";
        }
        foreach my $flow (@$flows) {
            my $output_text  = "";
            my $flow_matches = 1;
            my $priority     = $self->_unsign_int( $flow->{'priority'} );
            my $table_id     = $self->_unsign_int( $flow->{'tableId'} );
            my $cookie       = $self->_unsign_int( $flow->{'cookie'} );
            my $duration     = $self->_unsign_int( $flow->{'durationSeconds'} );
            my $idle_timeout = $self->_unsign_int( $flow->{'idleTimeout'} );
            my $hard_timeout = $self->_unsign_int( $flow->{'hardTimeout'} );
            my $packet_count = $self->_unsign_int( $flow->{'packetCount'} );
            my $byte_count   = $self->_unsign_int( $flow->{'byteCount'} );
            $output_text .= "Table ID: $table_id\tCookie: $cookie\n";
            $output_text .= "Priority: $priority Idle timeout(sec):$idle_timeout\tHard timeout(sec):$hard_timeout\n";
            $output_text .= "Packet Count: $packet_count\tByte Count:$byte_count\n";
            my $match = $flow->{'match'};
            $output_text .= "Match:\n";

            foreach my $key ( sort keys %$match ) {
                my $value = $match->{$key};

                #wildcards?
                # if ($vlan_id){
                #     if ($key eq 'dataLayerVirtualLan'){
                # 	if ($value == $vlan_id){
                # 	    $flow_matches =1;
                # 	}
                #     }
                # }
                if ( $value == 0 || $value eq '0.0.0.0' || $value eq '00:00:00:00:00:00' ) {
                    next;
                }

                #unsign ints
                if ( $value =~ /^-\d+$/ ) {
                    $value = $self->_unsign_int($value);
                }

                $output_text .= "$key:\t$value\n";
            }
            $output_text .= "Actions: ";

            my $actions = $flow->{'actions'};
            my @processed_actions;
            foreach my $action (@$actions) {
                my $type = $action->{'type'};

                my $action_str = "$type";

                foreach my $key ( sort keys %$action ) {

                    if ( $key eq 'length' || $key eq 'lengthU' || $key eq 'maxLength' || $key eq 'type' ) {
                        next;
                    }
                    my $value = $action->{$key};
                    if ( $value =~ /^-\d+$/ ) {
                        $value = $self->_unsign_int($value);
                    }
                    $action_str .= " $value";
                }

                #print "adding $action_str";
                push( @processed_actions, $action_str );

            }
            $output_text .= join( ",", @processed_actions ) . "\n\n";

            #if ($vlan_id || $port_id){
            #	if ($flow_matches){
            #	    print $output_text;
            #		}
            #	    }
            #else {
            print $output_text;

            #}
        }
    }
    elsif ( $input =~ /^show status (\S+) (\S+)/ ) {
        $ws->set_url("$base_url:$port/fsfw/status/$1/$2/json");
        my $status_obj = $ws->foo();

        #print Dumper ($status_obj);
        print "Status for $2 in slice $1:\n\n";
        foreach my $key ( sort keys %$status_obj ) {
            print "$key\t$status_obj->{$key}\n";

        }
    }elsif( $input =~ /^set slice status (\S+) (\S+) (\S+)/){
	my $status = $3;
	my $state;
	if($status eq 'enable'){
	    $state = 'true';
	}elsif($status eq 'disable'){
	    $state = 'false';
	}

	#warn "State is set to " . $state . "\n";

	if(!defined($state)){
	    print "Invalid state: " . $status . " must be enable/disable\n\n";
	}else{
	    $ws->set_url("$base_url:$port/fsfw/admin/set_state/$1/$2/$state/json");
	    my $status_obj = $ws->foo();
	    if($status_obj == 1){
		print "Slice $1 for DPID $2 was successfully " . $status . "d\n\n";
	    }else{
		print "An error occured attempting to set Slice $1 for DPID $2 to " . $status . "\n\n";
	    }
	}
		
    }

    return;    #$insert_text;
}

sub terminal_loop {

    my $self = shift;

    my $line;
    my $term = $self->{'term'};
    my $insert_text;
    my $preput = "";
    while ( defined( $line = $term->readline( $self->get_prompt(), $preput ) ) ) {

        $insert_text = $self->handle_input($line);
        if ($insert_text) {
            $preput = $line;
        }
        else {
            $preput = "";
        }

    }

}

#converts signed int to unsigned int;
sub _unsign_int {
    my $self = shift;
    my $int  = shift;

    return unpack( "S", pack( "s", $int ) );
}

1;
