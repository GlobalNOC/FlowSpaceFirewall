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

    $self->{config} = GRNOC::Config->new(config_file=>'/etc/fsfw/cli_config.xml');
    my $base_url = $self->{'config'}->get('/config/base_url');
    my $port = $self->{'config'}->get('/config/port');
    $base_url='http://localhost';
    $port='8080';
    $self->{history}       = [];
    $self->{history_index} = 0;
    
    
    $self->{'ws'} = GRNOC::WebService::Client->new(
	url => "$base_url:$port/admin/switches/json",
	uid => 'test_user',
	passwd => 'supersecretpassword',
	realm => 'foo',
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

    my @input_parts   = split( " ", $input );
    my $command_list  = $self->get_command_list();
    my $times_matched = 0;
    my $exact_match ={};
    foreach my $command (@$command_list) {
        my $matching_parts = 0;

        my @command_parts = split( " ", $command );
        for ( my $i = 0 ; $i < scalar(@input_parts) ; $i++ ) {
	   
	    if ($command_parts[$i] =~ /^$input_parts[$i]$/ ) {
		$exact_match->{$command} +=1;
	    }
            if ( $command_parts[$i] =~ /^$input_parts[$i].*/ ) {
                $matching_parts++;
            }


	    
        }
        if ( $matching_parts == scalar(@command_parts) ) {
            $new_text = $command;
            $times_matched++;
        }
	else {
		#has to match all parts to have any count in exact matches
		$exact_match->{$command} =0;
	}
    }
    if ( $new_text && $times_matched == 1 ) {
        return ( 0, $new_text );
    }
    elsif ( $times_matched < 1 ) {
        print "Command not found!\n";
    }
    elsif ( $times_matched > 1 ) {
	my $new_command;
	my $low_bar=0;
	foreach my $command (keys %$exact_match){
	    if ($exact_match->{$command} > $low_bar){
		$new_command=$command;
	    }
	}
	if($new_command){
	    warn "expanding $input to $new_command\n";
	    return (0, $new_command);
	}
        print "Command not unique!\n";
    }

    return ( 1, $input );
}

sub command_complete {
    my $self = shift;


    my ( $text, $line, $start ) = @_;
    my $command_list = $self->get_command_list();
    my @matches      = ();

    foreach my $command (@$command_list) {
        my $offset        = 0;
        my $is_match      = 1;
        my @text_parts    = split( " ", $line );
        my @command_parts = split( " ", $command );
        my $last_word     = $command_parts[0];

        for ( my $i = 0 ; $i < scalar(@text_parts) ; $i++ ) {
	    my $is_exact_match=0;
	    if ($command_parts[$i] =~ /^$text_parts[$i]$/ ) {
		$is_exact_match=1;
	    }
            unless ( $command_parts[$i] =~ /^$text_parts[$i].*/ ) {

                $is_match = 0;
                last;
            }
	    unless ($is_exact_match){
		$last_word = $command_parts[$i];
	    }
            if ( length( $text_parts[$i] ) == length( $command_parts[$i] ) ) {
                $last_word = $command_parts[ $i + 1 ];
            }

        }
        if ($is_match) {


            push( @matches, $last_word );
        }
    }


    return @matches;
}

#stubbed out in case we ever have a legitimate auth system for the WS.
sub login {

    my $self = shift;

    return 1;  

}

sub build_command_list {

    my $self = shift;
    my $ws = $self->{'ws'};

    my $base_url = $self->{'config'}->get('/config/base_url');
    my $port = $self->{'config'}->get('/config/port');
    $base_url='http://localhost';
    $port='8080';

    $self->{'possible_commands'} = [ 'show slices', 'show switches', 'quit', 'exit' ];  

    my @expandable_commands = ('show status','show flows');
    
    $ws->set_url("$base_url:$port/fsfw/admin/slices/json"); 
    my $slices_obj = $ws->foo();
    #warn Dumper ($slices_obj);
    my @slices;
    foreach my $slice (keys %$slices_obj){
	push (@slices, $slice);
    }

    $ws->set_url("$base_url:$port/fsfw/admin/switches/json"); 
    my $dpids_obj = $ws->foo();
    my $dpid_per_slice = {};
    foreach my $slice (@slices){
	$dpid_per_slice->{$slice}=[];
    }

    foreach my $switch (@$dpids_obj){
	foreach my $slice (@slices){
	    push @{$dpid_per_slice->{$slice}},$switch->{'dpid'}
	}
    }
    
    foreach my $expandable_command (@expandable_commands){
	foreach my $slice (@slices){
	    foreach my $dpid (@{$dpid_per_slice->{$slice}}){
		push (@{$self->{'possible_commands'}}, "$expandable_command $slice $dpid");
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
    my $self           = shift;
    my $input          = shift;
    my $insert_text = 0;
    my $ws = $self->{'ws'};
    my $base_url = $self->{'config'}->get('/config/base_url');
    my $port = $self->{'config'}->get('/config/port');
    $base_url='http://localhost';
    $port='8080';
    ( $insert_text, $input ) = $self->expand_commands($input);  
    
    if ( $input =~ /exit/ || $input =~ /quit/ ) {
        exit;
    }
    elsif ( $input =~ /^show switches$/ ) {
	$ws->set_url("$base_url:$port/fsfw/admin/switches/json"); 
	my $status_obj = $ws->foo();
#	print Dumper ($status_obj); #NONPROD
    
	foreach my $switch (@$status_obj){

	    my ($address) = $switch->{'inetAddress'} =~ /\/(\S+):\d+/;
	    print "IP:\t$address\n";
	    print "DPID:\t$switch->{'dpid'}\n";
	    print "Vendor:\t".$switch->{'descriptionStatistics'}->{'manufacturerDescription'}."\n";
	    print "Device:\t".$switch->{'descriptionStatistics'}->{'hardwareDescription'}."\n";
	    print "Software Version:\t".$switch->{'descriptionStatistics'}->{'softwareDescription'}."\n";
	    print "Ports:\n";
	    print "Name\tPort Number\tStatus\n\n";
	    #my $port_table=Text::Table->new("Port Name","Port Number", "Status");
	    foreach my $port (@{$switch->{'ports'}}) {
		
		my $port_num = $self->_unsign_int($port->{'portNumber'}); #unpack("S",pack("s",$port->{'portNumber'}));
#		$port_table->load([$port->{'name'},$port_num,'up']);
		print "$port->{'name'}\t$port_num\tUP\n";
#sprintf "%u\n", $port->{'portNumber'};
		
		
		
	    }
	    print "\n\n";
	}

    }
    elsif ( $input =~ /^show slices$/ ) {
	$ws->set_url("$base_url:$port/fsfw/admin/slices/json"); 
	my $slices = $ws->foo();
	print Dumper ($slices); #NONPROD
    }
    elsif ( $input =~/^show flows (\S+) (\S+)/){
	$ws->set_url("$base_url:$port/fsfw/flows/$1/$2/json"); 
	    my $flows = $ws->foo();
	    print Dumper ($flows); #NONPROD
	foreach my $flow (@$flows){
	    
	    my $priority = $self->_unsign_int($flow->{'priority'});
	    my $table_id = $self->_unsign_int($flow->{'tableId'});
	    my $cookie = $self->_unsign_int($flow->{'cookie'});
	    my $duration = $self->_unsign_int($flow->{'durationSeconds'});
	    my $idle_timeout = $self->_unsign_int($flow->{'idleTimeout'});
	    my $hard_timeout = $self->_unsign_int($flow->{'hardTimeout'});
	    my $packet_count = $self->_unsign_int($flow->{'packetCount'});
	    my $byte_count = $self->_unsign_int($flow->{'byteCount'});
	    print "Table ID: $table_id\tCookie: $cookie\n";
	    print "Priority: $priority Idle timeout(sec):$idle_timeout\tHard timeout(sec):$hard_timeout\n";
	    print "Packet Count: $packet_count\tByte Count:$byte_count\n";
	    my $match = $flow->{'match'};
	    print "Match:\n";
	    foreach my $key ( sort keys %$match){
		my $value = $match->{$key};
		#wildcards?
		if ($value == 0 || $value eq '0.0.0.0' || $value eq '00:00:00:00:00:00'){
		    next;
		}
		#unsign ints
		if ($value =~ /^-\d+$/ ){
		    $value = $self->_unsign_int($value);
		}

		print "\t$key:\t$value\n";
	    }
	    print "Actions: ";
	    
	    my $actions = $flow->{'actions'};
	    my @processed_actions;
	    foreach my $action (@$actions){
		my $type = $action->{'type'};
		
		my $action_str = "$type";
		
		foreach my $key (sort keys %$action){
		
		    if ($key eq 'length' || $key eq 'lengthU' || $key eq 'maxLength' || $key eq 'type'){
			next;
		    }
		    my $value = $action->{$key};
		    if ($value =~ /^-\d+$/ ){
			$value = $self->_unsign_int($value);
		    }
		    $action_str .= " $value";
		}
		#warn "adding $action_str";
		push (@processed_actions, $action_str);


	    }
	    print join(",",@processed_actions)."\n\n";
	}
    }
    elsif ( $input =~/^show status (\S+) (\S+)/){
	    $ws->set_url("$base_url:$port/fsfw/status/$1/$2/json"); 
	    my $status_obj = $ws->foo();
	    print Dumper ($status_obj); #NONPROD
    }

    return ;#$insert_text;
}

sub terminal_loop {

    my $self = shift;

    my $line;
    my $term = $self->{'term'};
    my $insert_text;
    my $preput="";
    while ( defined( $line = $term->readline( $self->get_prompt(),$preput ) ) ) {

        $insert_text = $self->handle_input($line);
	if ($insert_text){
	    $preput=$line;
	}
	else {
	    $preput="";
	}
	
    }

}


#converts signed int to unsigned int;
sub _unsign_int {
    my $self = shift;
    my $int = shift;

    return unpack("S",pack("s",$int));
}

1;
