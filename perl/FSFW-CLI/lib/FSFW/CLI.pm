#!/usr/bin/perl

#--------------------------------------------------------------------
#----- FSFW CLI library
#-----
#----- Copyright(C) 2014 The Trustees of Indiana University
#--------------------------------------------------------------------
#----- $HeadURL:
#----- $Id:
#-----
#----- cli code
#---------------------------------------------------------------------

package FSFW::CLI;
use strict;
use Term::ReadLine;    #using Term::ReadLine::Gnu
use GRNOC::Config;
use FindBin;
use Data::Dumper;
use GRNOC::WebService::Client;
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
    warn Dumper($self);
    $self->_init();

    return $self;

}

sub _init {
    my $self = shift;

    #$self->{config} = GRNOC::Config->new()
    $self->{history}       = [];
    $self->{history_index} = 0;
    
    
    $self->{'ws'} = GRNOC::WebService::Client->new(
	url => 'http://localhost:8080/admin/switches/json',
	uid => 'test_user',
	passwd => 'supersecretpassword',
	realm => 'foo',
	usePost => 0,
	);
    $self->build_command_list();
    
    $self->{'term'} = Term::ReadLine->new('FSFW CLI');
    $self->set_prompt('FSFW#');

    
    my $attribs = $self->{'term'}->Attribs;
    #diy completion function for the win.
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
    foreach my $command (@$command_list) {
        my $matching_parts = 0;
        my @command_parts = split( " ", $command );
        for ( my $i = 0 ; $i < scalar(@input_parts) ; $i++ ) {
            if ( $command_parts[$i] =~ /^$input_parts[$i].*/ ) {
                $matching_parts++;
            }
        }
        if ( $matching_parts == scalar(@command_parts) ) {
            $new_text = $command;
            $times_matched++;
        }
    }
    if ( $new_text && $times_matched == 1 ) {
        return ( 0, $new_text );
    }
    elsif ( $times_matched < 1 ) {
        print "Command not found!\n";
    }
    elsif ( $times_matched > 1 ) {
        print "Command not unique!\n";
    }

    #couldn't figure it out;
    return ( 1, $input );
}

sub command_complete {
    my $self = shift;

    #warn Dumper ($self);
    my ( $text, $line, $start ) = @_;
    my $command_list = $self->get_command_list();
    my @matches      = ();

    #warn Dumper $command_list;
    foreach my $command (@$command_list) {
        my $offset        = 0;
        my $is_match      = 1;
        my @text_parts    = split( " ", $line );
        my @command_parts = split( " ", $command );
        my $last_word     = $command_parts[0];

        #	unless (scalar(@$text_parts) ){
        #	    push (@matches,$command_parts[0]);
        #	}
        for ( my $i = 0 ; $i < scalar(@text_parts) ; $i++ ) {

            #warn "checking if $text_parts[$i] is in $command_parts[$i]\n";
            unless ( $command_parts[$i] =~ /^$text_parts[$i].*/ ) {

                #warn "it isn't\n";
                $is_match = 0;
                last;
            }
            $last_word = $command_parts[$i];
            if ( length( $text_parts[$i] ) == length( $command_parts[$i] ) ) {
                $last_word = $command_parts[ $i + 1 ];
            }

        }
        if ($is_match) {

            #warn "adding $last_word to matches\n";
            push( @matches, $last_word );
        }
    }

    #warn Dumper (\@matches);
    return @matches;
}

sub login {

    my $self = shift;

    return 1;    #NONPROD

}

sub build_command_list {

    my $self = shift;
    my $ws = $self->{'ws'};

    $self->{'possible_commands'} = [ 'show slices', 'show switches', 'quit', 'exit' ];    #NONPROD

    my @expandable_commands = ('show status','show flows');
    push (@{$self->{'possible_commands'} },@expandable_commands );
    
    $ws->set_url("http://localhost:8080/fsf/admin/slices/json"); #NONPROD
    my $slices_obj = $ws->foo();
    warn Dumper ($slices_obj);

    #$ws->set_url("http://localhost:8080/fsf/admin/switches/json"); #NONPROD
    #my $dpids_obj = $ws->foo();
    my $dpid_per_slice = {};
   
#    warn Dumper ($dpids_obj);

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
    print "Got Command : $input \n";    #NONPROD
    ( $insert_text, $input ) = $self->expand_commands($input);
    print "expanded input to $input\n";
    
    
    if ( $input =~ /exit/ || $input =~ /quit/ ) {
        exit;
    }
    elsif ( $input =~ /^show switches$/ ) {
        $self->show_switches();
    }

    return $insert_text;
}

sub terminal_loop {

    my $self = shift;

    #my $process_input = shift;
    #my $command_complete = shift;

    #$self->print_prompt();
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

1;
